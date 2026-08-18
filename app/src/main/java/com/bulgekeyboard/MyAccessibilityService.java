package com.bulgekeyboard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.hardware.*;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.WindowManager;
import android.util.Log;

public class MyAccessibilityService extends AccessibilityService implements SensorEventListener {

    public static MyAccessibilityService instance = null;
    public static CursorOverlay overlay = null;
    private SensorManager sm;
    private Sensor sensor;
    private Sensor proximitySensor;
    private Sensor accel;
    private PowerManager.WakeLock wakeLock;
    private Vibrator vibrator;

    private boolean isPocketed = false;
    private boolean isScreenOn = true;
    private float sens = 1.0f, speed = 1.0f, smoothing = 20.0f;
    private int offX = 0, offY = 0;
    private long lastPrefUpdate = 0;

    private float sDx = 0, sDy = 0;
    private long lastMoveTime = 0;

    // Interaction variables
    private boolean isScrolling = false;
    private int scrollZone = 0; 
    private long lastScrollTime = 0;
    private String currentPackage = "";

    private long cornerStartTime = 0;
    private boolean cornerActionTriggered = false;

    private long keyDownTime = 0;
    private boolean isStickyDragging = false;
    private boolean isLongPressProcessed = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable = null;
    private Runnable volLongPressRunnable = null;

    private int clickCount = 0;
    private final Runnable clickProcessor = new Runnable() {
        @Override
        public void run() {
            if (clickCount == 1) {
                vibrate(30);
                overlay.showClickFeedback();
                performClick(false);
            } else if (clickCount >= 2) {
                vibrate(60);
                performDoubleClick();
            }
            clickCount = 0;
        }
    };

    private final Runnable autoHideRunnable = new Runnable() {
        @Override
        public void run() {
            if (overlay != null && System.currentTimeMillis() - lastMoveTime > 10000) {
                overlay.setHidden(true);
            }
            handler.postDelayed(this, 1000);
        }
    };

    private GestureDescription.StrokeDescription lastStroke = null;
    private int lastX, lastY;
    private long lastDragUpdateTime = 0;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                isScreenOn = false;
                optimizeForBattery(true);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                isScreenOn = true;
                optimizeForBattery(false);
            }
        }
    };

    private int volUpClickCount = 0;
    private final Runnable volUpClickProcessor = new Runnable() {
        @Override
        public void run() {
            if (volUpClickCount == 1) {
                // Single click logic handled in onKeyEvent
            } else if (volUpClickCount >= 2) {
                // 🔥 Double Click: Exit Keyboard
                if (BulgeKeyboardService.instance != null) {
                    BulgeKeyboardService.instance.hideSelf();
                    vibrate(100);
                }
            }
            volUpClickCount = 0;
        }
    };

    @Override
    protected void onServiceConnected() {
        instance = this;
        handler.post(autoHideRunnable);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "CustomMouse:WakeLock");
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);

        sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (sensor == null) sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        proximitySensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        if (proximitySensor != null) sm.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        if (accel != null) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
    }

    private void optimizeForBattery(boolean sleep) {
        sm.unregisterListener(this);
        if (sleep) {
            if (accel != null) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL);
            if (proximitySensor != null) sm.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
            if (accel != null) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL);
            if (proximitySensor != null) sm.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            isPocketed = event.values[0] < event.sensor.getMaximumRange();
            return;
        }

        if (!isScreenOn) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && !isPocketed) {
                detectShake(event.values);
            }
            return;
        }

        if (overlay == null || isPocketed) return;

        // 🔥 New: Hide cursor if keyboard is active
        boolean isKbActive = BulgeKeyboardService.instance != null && BulgeKeyboardService.instance.isInputViewShown();
        overlay.setHidden(isKbActive);
        if (isKbActive) return; // Stop processing mouse while typing

        long now = System.currentTimeMillis();
        if (now - lastPrefUpdate > 1000) {
            SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
            sens = prefs.getInt("sens", 100) / 100f;
            speed = prefs.getInt("speed", 100) / 100f;
            smoothing = prefs.getInt("smoothing", 20);
            offX = prefs.getInt("clickX", 0);
            offY = prefs.getInt("clickY", 0);
            if (overlay != null) {
                overlay.setBlurEnabled(prefs.getBoolean("blur", false));
                overlay.setCursorSize(prefs.getInt("size", 100));
                overlay.setBoxColor(prefs.getInt("boxColor", 0x4DFFFFFF));
            }
            lastPrefUpdate = now;
        }

        if (event.sensor.getType() != Sensor.TYPE_GAME_ROTATION_VECTOR && event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) return;

        float rawDx, rawDy;
        if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            rawDx = event.values[1] * (60 * sens);
            rawDy = event.values[0] * (60 * speed);
        } else {
            rawDx = -event.values[0] * (10 * sens);
            rawDy = event.values[1] * (10 * speed);
        }

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int rotation = wm.getDefaultDisplay().getRotation();
        float dx, dy;
        switch (rotation) {
            case Surface.ROTATION_90: dx = rawDy; dy = -rawDx; break;
            case Surface.ROTATION_180: dx = -rawDx; dy = -rawDy; break;
            case Surface.ROTATION_270: dx = -rawDy; dy = rawDx; break;
            default: dx = rawDx; dy = rawDy; break;
        }

        overlay.updateDisplay();
        float mag = (float) Math.sqrt(dx * dx + dy * dy);
        if (mag > 0) {
            float scale = (float) Math.pow(mag, 0.2);
            dx *= scale * 0.5f; dy *= scale * 0.5f;
        }

        if (Math.abs(dx) < 1.5f && Math.abs(dy) < 1.5f) return;
        
        float sFactor = 1.0f / Math.max(1, smoothing);
        sDx = sDx + sFactor * (dx - sDx);
        sDy = sDy + sFactor * (dy - sDy);

        overlay.setHidden(false);
        lastMoveTime = now;
        overlay.move((int) sDx, (int) sDy, offX, offY);

        if (isScrolling) handleContinuousScroll(now, dy, dx);
        checkCornerAction(now);

        if (isStickyDragging && lastStroke != null) {
            long nowDrag = System.currentTimeMillis();
            if (nowDrag - lastDragUpdateTime > 20) {
                int[] pos = overlay.getRealPos();
                updateStickyDrag(pos[0] + offX, pos[1] + offY);
                lastDragUpdateTime = nowDrag;
            }
        }

        if (wakeLock != null) wakeLock.acquire(10000);
    }

    private void detectShake(float[] v) {
        float x = v[0], y = v[1], z = v[2];
        float totalAccel = (float) Math.sqrt(x*x + y*y + z*z);
        if (totalAccel > 45.0f) { 
            if (wakeLock != null) {
                wakeLock.acquire(1000);
                vibrate(100);
            }
        }
    }

    private void handleContinuousScroll(long now, float dy, float dx) {
        if (now - lastScrollTime > 300) {
            float tilt = (scrollZone == 1 || scrollZone == 2) ? Math.abs(dy) : Math.abs(dx);
            if (tilt > 8.0f) {
                int dist = (int)(tilt * 35);
                switch(scrollZone) {
                    case 1: performGesture(0, dist, 200); break; 
                    case 2: performGesture(0, -dist, 200); break; 
                    case 3: performGesture(dist, 0, 200); break; 
                    case 4: performGesture(-dist, 0, 200); break; 
                }
                lastScrollTime = now; vibrate(10);
            }
        }
    }

    private void checkCornerAction(long now) {
        if (overlay == null) return;
        int[] pos = overlay.getRealPos(); int tx = pos[0] + offX, ty = pos[1] + offY;
        int sw = getResources().getDisplayMetrics().widthPixels;
        if (tx > sw - 30 && ty < 30) {
            if (cornerStartTime == 0) { cornerStartTime = now; cornerActionTriggered = false; }
            else if (!cornerActionTriggered && now - cornerStartTime > 1000) {
                vibrate(100); performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS); cornerActionTriggered = true;
            }
        } else { cornerStartTime = 0; cornerActionTriggered = false; }
    }

    private void performGesture(int dx, int dy, int duration) {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics(); 
        wm.getDefaultDisplay().getRealMetrics(metrics);
        int sw = metrics.widthPixels, sh = metrics.heightPixels;
        int xs = sw / 2, ys = sh / 2;
        int xe = Math.max(0, Math.min(sw - 1, xs + dx)), ye = Math.max(0, Math.min(sh - 1, ys + dy));
        Path p = new Path(); p.moveTo(xs, ys); p.lineTo(xe, ye);
        GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(p, 0, duration);
        dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
    }

    private void performEdgeGesture(int zone, int duration) {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics(); 
        wm.getDefaultDisplay().getRealMetrics(metrics);
        int sw = metrics.widthPixels, sh = metrics.heightPixels;

        int xs = 0, ys = 0, xe = 0, ye = 0;
        int[] pos = overlay.getRealPos();
        int curX = pos[0] + offX;
        int curY = pos[1] + offY;

        switch (zone) {
            case 1: // Top -> Swipe Down
                xs = curX; ys = 5; xe = curX; ye = (int)(sh * 0.7f); break;
            case 2: // Bottom -> Swipe Up
                xs = curX; ys = sh - 5; xe = curX; ye = (int)(sh * 0.3f); break;
            case 3: // Left -> Swipe Right
                xs = 5; ys = sh / 2; xe = (int)(sw * 0.8f); ye = sh / 2; break;
            case 4: // Right -> Swipe Left
                xs = sw - 5; ys = sh / 2; xe = (int)(sw * 0.2f); ye = sh / 2; break;
        }

        Path p = new Path(); p.moveTo(xs, ys); p.lineTo(xe, ye);
        GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(p, 0, duration);
        dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
    }

    private void performDoubleClick() {
        int[] pos = overlay.getRealPos(); int x = Math.max(0, pos[0] + offX), y = Math.max(0, pos[1] + offY);
        Path p = new Path(); p.moveTo(x, y); p.lineTo(x, y);
        GestureDescription.StrokeDescription s1 = new GestureDescription.StrokeDescription(p, 0, 50);
        GestureDescription.StrokeDescription s2 = new GestureDescription.StrokeDescription(p, 150, 50);
        dispatchGesture(new GestureDescription.Builder().addStroke(s1).addStroke(s2).build(), null, null);
    }

    private int keyboardPowerClickCount = 0;
    private final Runnable keyboardPowerClickProcessor = new Runnable() {
        @Override
        public void run() {
            if (keyboardPowerClickCount == 1) {
                BulgeKeyboardService.instance.typeFocused();
                vibrate(30);
            } else if (keyboardPowerClickCount >= 2) {
                BulgeKeyboardService.instance.handlePowerDoubleClick();
                vibrate(60);
            }
            keyboardPowerClickCount = 0;
        }
    };

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        int action = event.getAction();

        // 🔥 Detect if our Bulge Keyboard is currently open
        boolean isKeyboardActive = BulgeKeyboardService.instance != null && BulgeKeyboardService.instance.isInputViewShown();

        if (key == KeyEvent.KEYCODE_VOLUME_UP) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (isKeyboardActive) {
                    // 🔥 Keyboard Mode: Double Click Detection
                    handler.removeCallbacks(volUpClickProcessor);
                    volUpClickCount++;
                    if (volUpClickCount == 1) {
                        BulgeKeyboardService.instance.handleEnter();
                        vibrate(40);
                    }
                    handler.postDelayed(volUpClickProcessor, 300);
                } else {
                    // 🔥 Mouse Mode: Swipe/Scroll Trigger
                    isLongPressProcessed = false;
                    int zone = overlay.getCursorZone(offX, offY);
                    if (zone > 0) {
                        volLongPressRunnable = () -> {
                            isLongPressProcessed = true; isScrolling = !isScrolling;
                            scrollZone = zone; vibrate(isScrolling ? 150 : 80);
                        };
                        handler.postDelayed(volLongPressRunnable, 600);
                    }
                }
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                if (!isKeyboardActive) {
                    if (volLongPressRunnable != null) handler.removeCallbacks(volLongPressRunnable);
                    if (!isLongPressProcessed) {
                        int zone = overlay.getCursorZone(offX, offY);
                        if (zone > 0) { isScrolling = false; executeSwipe(zone); }
                    }
                }
                return true;
            }
        }

        if (key == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (action == KeyEvent.ACTION_DOWN && isKeyboardActive) {
                // 🔥 Keyboard Mode: Backspace
                BulgeKeyboardService.instance.handleBackspace();
                vibrate(30);
                return true;
            }
        }

        if (key == KeyEvent.KEYCODE_POWER || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                isLongPressProcessed = false;
                longPressRunnable = () -> {
                    isLongPressProcessed = true;
                    if (isKeyboardActive) {
                        // 🔥 Keyboard Mode: Type Secondary
                        BulgeKeyboardService.instance.typeSecondary();
                        vibrate(100);
                    } else if (!CalibrationActivity.isRunning) {
                        // 🔥 Mouse Mode: Sticky Drag
                        vibrate(100); isStickyDragging = !isStickyDragging;
                        overlay.setDraggingVisual(isStickyDragging);
                        int[] pos = overlay.getRealPos();
                        if (isStickyDragging) startStickyDrag(pos[0] + offX, pos[1] + offY);
                        else stopStickyDrag(pos[0] + offX, pos[1] + offY);
                    }
                };
                handler.postDelayed(longPressRunnable, 1200);
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                if (longPressRunnable != null) handler.removeCallbacks(longPressRunnable);
                if (CalibrationActivity.isRunning) sendBroadcast(new Intent("COM_EXAMPLE_CUSTOMMOUSE_CALIBRATE"));
                else if (!isLongPressProcessed) {
                    if (isKeyboardActive) {
                        // 🔥 Keyboard Mode: Normal Click / Double Click
                        handler.removeCallbacks(keyboardPowerClickProcessor);
                        keyboardPowerClickCount++;
                        handler.postDelayed(keyboardPowerClickProcessor, 300);
                    } else {
                        // 🔥 Mouse Mode: Normal Click
                        handler.removeCallbacks(clickProcessor); clickCount++;
                        if (isStickyDragging) {
                            isStickyDragging = false; overlay.setDraggingVisual(false);
                            stopStickyDrag(overlay.getRealPos()[0] + offX, overlay.getRealPos()[1] + offY);
                            vibrate(40); clickCount = 0;
                        } else handler.postDelayed(clickProcessor, 300);
                    }
                }
                isLongPressProcessed = false; return true;
            }
        }
        return super.onKeyEvent(event);
    }

    private void executeSwipe(int zone) {
        vibrate(60);
        if ("com.android.systemui".equals(currentPackage)) {
            performEdgeGesture(zone, 300);
        } else {
            switch(zone) {
                case 1: performGesture(0, 800, 300); break; 
                case 2: performGesture(0, -800, 300); break; 
                case 3: performGesture(800, 0, 300); break; 
                case 4: performGesture(-800, 0, 300); break;
            }
        }
    }

    private void vibrate(int ms) {
        if (vibrator == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(ms, -1));
        else vibrator.vibrate(ms);
    }

    public void performClick(boolean hold) {
        int[] pos = overlay.getRealPos(); int x = Math.max(0, pos[0] + offX), y = Math.max(0, pos[1] + offY);
        Path p = new Path(); p.moveTo(x, y); p.lineTo(x, y);
        GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(p, 0, hold ? 60000 : 50);
        dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
    }

    private void startStickyDrag(int x, int y) {
        Path p = new Path(); p.moveTo(x, y);
        lastStroke = new GestureDescription.StrokeDescription(p, 0, 100, true);
        lastX = x; lastY = y;
        dispatchGesture(new GestureDescription.Builder().addStroke(lastStroke).build(), null, null);
    }

    private void updateStickyDrag(int x, int y) {
        if (lastStroke == null) return;
        Path p = new Path(); p.moveTo(lastX, lastY); p.lineTo(x, y);
        lastStroke = lastStroke.continueStroke(p, 0, 40, true);
        lastX = x; lastY = y;
        dispatchGesture(new GestureDescription.Builder().addStroke(lastStroke).build(), null, null);
    }

    private void stopStickyDrag(int x, int y) {
        if (lastStroke == null) return;
        Path p = new Path(); p.moveTo(lastX, lastY); p.lineTo(x, y);
        lastStroke = lastStroke.continueStroke(p, 0, 50, false);
        dispatchGesture(new GestureDescription.Builder().addStroke(lastStroke).build(), null, null);
        lastStroke = null;
    }

    private void checkAutoHide() { if (overlay != null && System.currentTimeMillis() - lastMoveTime > 10000) overlay.setHidden(true); }
    @Override public void onAccuracyChanged(Sensor s, int a) {}
    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent e) { 
        if (e.getPackageName() != null) currentPackage = e.getPackageName().toString();
    }
    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
    }
}