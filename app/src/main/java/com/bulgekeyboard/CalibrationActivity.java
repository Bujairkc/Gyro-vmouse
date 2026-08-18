package com.bulgekeyboard;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

public class CalibrationActivity extends Activity {

    public static boolean isRunning = false;
    int cx, cy;

    private final BroadcastReceiver calibrateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            doCalibration();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        isRunning = true;

        // 🔥 Force Full Screen / Immersive and Layout in Screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(calibrateReceiver, new IntentFilter("COM_EXAMPLE_CUSTOMMOUSE_CALIBRATE"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(calibrateReceiver, new IntentFilter("COM_EXAMPLE_CUSTOMMOUSE_CALIBRATE"));
        }
        
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);

        // 🔥 Use real metrics to align with the overlay's coordinate system
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int w = metrics.widthPixels;
        int h = metrics.heightPixels;

        cx = w / 2;
        cy = h / 2;

        View v = new View(this);
        v.setBackgroundColor(Color.RED);
        FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(3, 100);
        vp.leftMargin = cx - 1;
        vp.topMargin = cy - 50;

        View hLine = new View(this);
        hLine.setBackgroundColor(Color.RED);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(100, 3);
        hp.leftMargin = cx - 50;
        hp.topMargin = cy - 1;

        root.addView(v, vp);
        root.addView(hLine, hp);

        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        try {
            unregisterReceiver(calibrateReceiver);
        } catch (Exception ignored) {}
    }

    private boolean isCalibrating = false;

    private void doCalibration() {
        if (isCalibrating) return;
        isCalibrating = true;

        if (MyAccessibilityService.overlay == null) {
            finish();
            return;
        }

        int[] pos = MyAccessibilityService.overlay.getRealPos();

        int offsetX = cx - pos[0];
        int offsetY = cy - pos[1];

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        prefs.edit()
                .putInt("clickX", offsetX)
                .putInt("clickY", offsetY)
                .apply();

        Toast.makeText(this, "Calibrated! Center: (" + cx + "," + cy + ") Cursor: (" + pos[0] + "," + pos[1] + ") Offset: " + offsetX + "," + offsetY, Toast.LENGTH_LONG).show();

        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_POWER) {

            doCalibration();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
}