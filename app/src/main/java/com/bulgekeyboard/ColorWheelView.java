package com.bulgekeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ColorWheelView extends View {
    private Paint colorWheelPaint;
    private Paint thumbPaint;
    private float radius;
    private float centerX;
    private float centerY;
    private float thumbX;
    private float thumbY;
    private int selectedColor = Color.WHITE;

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    private OnColorSelectedListener listener;

    public ColorWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        colorWheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setStyle(Paint.Style.STROKE);
        thumbPaint.setStrokeWidth(8);
        thumbPaint.setColor(Color.WHITE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(centerX, centerY) - 20;

        Shader hueShader = new SweepGradient(centerX, centerY,
                new int[]{Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED},
                null);
        Shader saturationShader = new RadialGradient(centerX, centerY, radius,
                Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP);
        colorWheelPaint.setShader(new ComposeShader(hueShader, saturationShader, PorterDuff.Mode.SRC_OVER));
        
        updateThumbPosition();
    }

    private void updateThumbPosition() {
        float[] hsv = new float[3];
        Color.colorToHSV(selectedColor, hsv);
        double angle = Math.toRadians(hsv[0]);
        float r = hsv[1] * radius;
        thumbX = centerX + (float) (r * Math.cos(angle));
        thumbY = centerY + (float) (r * Math.sin(angle));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawCircle(centerX, centerY, radius, colorWheelPaint);
        canvas.drawCircle(thumbX, thumbY, 30, thumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX() - centerX;
        float y = event.getY() - centerY;
        float d = (float) Math.sqrt(x * x + y * y);

        if (d > radius) {
            float scale = radius / d;
            x *= scale;
            y *= scale;
            d = radius;
        }

        thumbX = centerX + x;
        thumbY = centerY + y;

        double angle = Math.atan2(y, x);
        float hue = (float) Math.toDegrees(angle);
        if (hue < 0) hue += 360f;
        float saturation = d / radius;

        selectedColor = Color.HSVToColor(new float[]{hue, saturation, 1f});
        if (listener != null) listener.onColorSelected(selectedColor);
        
        invalidate();
        return true;
    }

    public void setColor(int color) {
        this.selectedColor = color;
        updateThumbPosition();
        invalidate();
    }

    public int getSelectedColor() {
        return selectedColor;
    }

    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        this.listener = listener;
    }
}