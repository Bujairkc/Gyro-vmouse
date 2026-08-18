package com.bulgekeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class CursorOverlay {

    private WindowManager wm;
    private FrameLayout root;
    private ImageView cursor;
    private ImageView[] ghosts = new ImageView[3];
    private List<int[]> history = new ArrayList<>();
    private View boxTop, boxBottom, boxLeft, boxRight;
    private WindowManager.LayoutParams params;

    private int x = 300;
    private int y = 500;
    private int screenWidth, screenHeight;
    private boolean isHidden = false;
    private boolean blurEnabled = false;
    private int cursorSize = 100;
    private int boxColor = Color.parseColor("#4DFFFFFF");

    private final int boxSize = 100;

    public CursorOverlay(Context ctx) {
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;

        SharedPreferences prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
        blurEnabled = prefs.getBoolean("blur", false);
        cursorSize = prefs.getInt("size", 100);
        boxColor = prefs.getInt("boxColor", Color.parseColor("#4DFFFFFF"));

        root = new FrameLayout(ctx);
        
        // Create ghosts
        for (int i = 0; i < ghosts.length; i++) {
            ghosts[i] = new ImageView(ctx);
            ghosts[i].setAlpha(0.3f - (i * 0.1f));
            ghosts[i].setVisibility(View.GONE);
            root.addView(ghosts[i], new FrameLayout.LayoutParams(-2, -2));
        }

        cursor = new ImageView(ctx);
        loadCursorImage(ctx);
        updateCursorSize();

        // Version Label
        TextView versionLabel = new TextView(ctx);
        versionLabel.setText("v2.9.6");
        versionLabel.setTextColor(Color.GREEN);
        versionLabel.setTextSize(10);
        versionLabel.setAlpha(0.5f);
        FrameLayout.LayoutParams vLp = new FrameLayout.LayoutParams(-2, -2);
        vLp.gravity = Gravity.TOP | Gravity.END;
        vLp.setMarginEnd(20);
        vLp.topMargin = 20;
        root.addView(versionLabel, vLp);

        // Following boxes
        boxTop = createEdgeBox(ctx);
        boxBottom = createEdgeBox(ctx);
        boxLeft = createEdgeBox(ctx);
        boxRight = createEdgeBox(ctx);

        root.addView(boxTop, getLp(Gravity.TOP | Gravity.START));
        root.addView(boxBottom, getLp(Gravity.BOTTOM | Gravity.START));
        root.addView(boxLeft, getLp(Gravity.TOP | Gravity.START));
        root.addView(boxRight, getLp(Gravity.TOP | Gravity.END));

        FrameLayout.LayoutParams cursorLp = new FrameLayout.LayoutParams(-2, -2);
        cursorLp.gravity = Gravity.TOP | Gravity.START;
        root.addView(cursor, cursorLp);

        // 🔥 CRITICAL: Force ACCESSIBILITY_OVERLAY and Screen-wide dimensions
        params = new WindowManager.LayoutParams(
                screenWidth,
                screenHeight,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        // Fix for Android 11+ to ignore system bar cutouts/insets
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }

        params.gravity = Gravity.TOP | Gravity.START;
        wm.addView(root, params);
        updateCursorPos();
    }

    private View createEdgeBox(Context ctx) {
        View v = new View(ctx);
        updateBoxStyle(v);
        v.setLayoutParams(new FrameLayout.LayoutParams(boxSize, boxSize));
        v.setAlpha(0.0f);
        return v;
    }

    private void updateBoxStyle(View v) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(boxColor);
        gd.setCornerRadius(boxSize / 2f);
        v.setBackground(gd);
    }

    public void setBoxColor(int color) {
        this.boxColor = color;
        updateBoxStyle(boxTop);
        updateBoxStyle(boxBottom);
        updateBoxStyle(boxLeft);
        updateBoxStyle(boxRight);
    }

    public void setCursorSize(int size) {
        this.cursorSize = size;
        updateCursorSize();
    }

    private void updateCursorSize() {
        float scale = cursorSize / 100f;
        cursor.setScaleX(scale);
        cursor.setScaleY(scale);
        for (ImageView g : ghosts) {
            g.setScaleX(scale);
            g.setScaleY(scale);
        }
    }

    private FrameLayout.LayoutParams getLp(int gravity) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(boxSize, boxSize);
        lp.gravity = gravity;
        return lp;
    }

    public void setBlurEnabled(boolean enabled) {
        this.blurEnabled = enabled;
        if (!enabled) {
            for (ImageView g : ghosts) g.setVisibility(View.GONE);
            history.clear();
        }
    }

    public int getCursorZone(int offX, int offY) {
        int tipX = x + offX;
        int tipY = y + offY;
        if (tipY < boxSize) return 1;
        if (tipY > screenHeight - boxSize) return 2;
        if (tipX < boxSize) return 3;
        if (tipX > screenWidth - boxSize) return 4;
        return 0;
    }

    public void move(int dx, int dy, int offX, int offY) {
        x += dx; y += dy;
        if (x < -offX) x = -offX;
        if (y < -offY) y = -offY;
        if (x > screenWidth - offX) x = screenWidth - offX;
        if (y > screenHeight - offY) y = screenHeight - offY;
        updateCursorPos();
        updateBoxPositions(offX, offY);
    }

    public void setPos(int nx, int ny, int ox, int oy) {
        this.x = nx; this.y = ny;
        updateCursorPos();
        updateBoxPositions(ox, oy);
    }

    private void updateCursorPos() {
        cursor.setTranslationX(x);
        cursor.setTranslationY(y);

        if (blurEnabled) {
            history.add(0, new int[]{x, y});
            if (history.size() > 10) history.remove(history.size() - 1);
            for (int i = 0; i < ghosts.length; i++) {
                int index = (i + 1) * 2;
                if (history.size() > index) {
                    int[] p = history.get(index);
                    ghosts[i].setTranslationX(p[0]);
                    ghosts[i].setTranslationY(p[1]);
                    ghosts[i].setVisibility(View.VISIBLE);
                } else { ghosts[i].setVisibility(View.GONE); }
            }
        }
    }

    private void updateBoxPositions(int offX, int offY) {
        int tipX = x + offX;
        int tipY = y + offY;
        int showThreshold = 350;
        boxLeft.setTranslationY(tipY - boxSize / 2f);
        boxRight.setTranslationY(tipY - boxSize / 2f);
        boxTop.setTranslationX(tipX - boxSize / 2f);
        boxBottom.setTranslationX(tipX - boxSize / 2f);
        boxTop.setAlpha(getAlpha(tipY, 0, showThreshold));
        boxBottom.setAlpha(getAlpha(screenHeight - tipY, 0, showThreshold));
        boxLeft.setAlpha(getAlpha(tipX, 0, showThreshold));
        boxRight.setAlpha(getAlpha(screenWidth - tipX, 0, showThreshold));
    }

    private float getAlpha(float val, int target, int threshold) {
        float dist = Math.abs(val - target);
        if (dist > threshold) return 0.0f;
        return (1.0f - (dist / threshold)) * 0.6f;
    }

    public int[] getRealPos() { return new int[]{x, y}; }

    public void updateDisplay() {
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        screenWidth = m.widthPixels;
        screenHeight = m.heightPixels;
    }

    public void showClickFeedback() {
        cursor.animate().scaleX(0.8f).scaleY(0.8f).setDuration(50)
              .withEndAction(() -> cursor.animate().scaleX(1.0f).scaleY(1.0f).setDuration(50).start()).start();
    }

    public void setDraggingVisual(boolean d) {
        cursor.setScaleX(d ? 0.85f : 1.0f);
        cursor.setScaleY(d ? 0.85f : 1.0f);
    }

    public void setHidden(boolean h) {
        if (isHidden == h) return;
        isHidden = h;
        root.animate().alpha(h ? 0.2f : 1.0f).setDuration(300).start();
    }

    public void loadCursorImage(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String u = p.getString("cursorUri", null);
        Uri uri = (u != null) ? Uri.parse(u) : null;
        applyImage(cursor, uri);
        for (ImageView g : ghosts) applyImage(g, uri);
    }

    private void applyImage(ImageView iv, Uri uri) {
        if (uri != null) {
            try { iv.setImageURI(uri); } catch (Exception e) { iv.setImageResource(R.drawable.pc_cursor_arrow); }
        } else { iv.setImageResource(R.drawable.pc_cursor_arrow); }
    }

    public void remove() { try { wm.removeView(root); } catch (Exception ignored) {} }
}