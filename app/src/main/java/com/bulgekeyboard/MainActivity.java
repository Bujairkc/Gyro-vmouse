package com.bulgekeyboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    Button startBtn, stopBtn, calibrateBtn, pickCursorBtn, resetCursorBtn, customColorBtn;
    Button enableKeyboardBtn, switchKeyboardBtn;
    SeekBar sensitivity, speed, smoothing, mouseSize;
    TextView sensText, speedText, smoothText, sizeText;
    CheckBox blurCheck, revolverModeCheck, systemEmojiCheck;

    SharedPreferences prefs;

    private final ActivityResultLauncher<String[]> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    prefs.edit().putString("cursorUri", uri.toString()).apply();
                    if (MyAccessibilityService.overlay != null) MyAccessibilityService.overlay.loadCursorImage(this);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        startBtn = findViewById(R.id.startBtn);
        stopBtn = findViewById(R.id.stopBtn);
        calibrateBtn = findViewById(R.id.calibrateBtn);
        pickCursorBtn = findViewById(R.id.pickCursorBtn);
        resetCursorBtn = findViewById(R.id.resetCursorBtn);
        customColorBtn = findViewById(R.id.customColorBtn);
        enableKeyboardBtn = findViewById(R.id.enableKeyboardBtn);
        switchKeyboardBtn = findViewById(R.id.switchKeyboardBtn);

        sensitivity = findViewById(R.id.sensitivity);
        speed = findViewById(R.id.speed);
        smoothing = findViewById(R.id.smoothing);
        mouseSize = findViewById(R.id.mouseSize);

        sensText = findViewById(R.id.sensText);
        speedText = findViewById(R.id.speedText);
        smoothText = findViewById(R.id.smoothText);
        sizeText = findViewById(R.id.sizeText);
        blurCheck = findViewById(R.id.blurCheck);
        revolverModeCheck = findViewById(R.id.revolverModeCheck);
        systemEmojiCheck = findViewById(R.id.systemEmojiCheck);

        int sensVal = prefs.getInt("sens", 100);
        int speedVal = prefs.getInt("speed", 100);
        int smoothVal = prefs.getInt("smoothing", 20);
        int sizeVal = prefs.getInt("size", 100);
        boolean blurVal = prefs.getBoolean("blur", false);
        boolean revolverVal = prefs.getBoolean("revolver_mode", false);
        boolean systemEmojiVal = prefs.getBoolean("system_emojis", false);

        sensitivity.setProgress(sensVal);
        speed.setProgress(speedVal);
        smoothing.setProgress(smoothVal);
        mouseSize.setProgress(sizeVal);
        blurCheck.setChecked(blurVal);
        revolverModeCheck.setChecked(revolverVal);
        systemEmojiCheck.setChecked(systemEmojiVal);

        sensText.setText("Sensitivity: " + sensVal);
        speedText.setText("Speed: " + speedVal);
        smoothText.setText("Smoothing: " + smoothVal);
        sizeText.setText("Cursor Scale: " + sizeVal + "%");

        blurCheck.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.edit().putBoolean("blur", isChecked).apply();
            if (MyAccessibilityService.overlay != null) {
                MyAccessibilityService.overlay.setBlurEnabled(isChecked);
            }
        });

        revolverModeCheck.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.edit().putBoolean("revolver_mode", isChecked).apply();
            Toast.makeText(this, "Revolver Mode: " + (isChecked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        });

        systemEmojiCheck.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.edit().putBoolean("system_emojis", isChecked).apply();
            Toast.makeText(this, "System Emojis: " + (isChecked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        });

        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                prefs.edit().putInt("sens", v).apply();
                sensText.setText("Sensitivity: " + v);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                prefs.edit().putInt("speed", v).apply();
                speedText.setText("Speed: " + v);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        smoothing.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                prefs.edit().putInt("smoothing", v).apply();
                smoothText.setText("Smoothing: " + v);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        mouseSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                prefs.edit().putInt("size", v).apply();
                sizeText.setText("Cursor Scale: " + v + "%");
                if (MyAccessibilityService.overlay != null) MyAccessibilityService.overlay.setCursorSize(v);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        customColorBtn.setOnClickListener(v -> showColorPicker());

        startBtn.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
                return;
            }
            if (MyAccessibilityService.instance == null) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            if (MyAccessibilityService.overlay == null) {
                MyAccessibilityService.overlay = new CursorOverlay(MyAccessibilityService.instance);
            }
        });

        stopBtn.setOnClickListener(v -> {
            if (MyAccessibilityService.overlay != null) {
                MyAccessibilityService.overlay.remove();
                MyAccessibilityService.overlay = null;
            }
        });

        calibrateBtn.setOnClickListener(v -> startActivity(new Intent(this, CalibrationActivity.class)));
        pickCursorBtn.setOnClickListener(v -> pickImageLauncher.launch(new String[]{"image/*"}));
        resetCursorBtn.setOnClickListener(v -> {
            prefs.edit().remove("cursorUri").apply();
            if (MyAccessibilityService.overlay != null) MyAccessibilityService.overlay.loadCursorImage(this);
        });

        // 🔥 KEYBOARD CONTROLS
        enableKeyboardBtn.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        });

        switchKeyboardBtn.setOnClickListener(v -> {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        });
    }

    private void saveColor(int color) {
        prefs.edit().putInt("boxColor", color).apply();
        if (MyAccessibilityService.overlay != null) {
            MyAccessibilityService.overlay.setBoxColor(color);
        }
        Toast.makeText(this, "Color applied", Toast.LENGTH_SHORT).show();
    }

    private void showColorPicker() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Box Color");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 40);

        final ColorWheelView colorWheel = new ColorWheelView(this, null);
        android.widget.LinearLayout.LayoutParams wheelParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 600);
        colorWheel.setLayoutParams(wheelParams);
        
        int currentColor = prefs.getInt("boxColor", 0x4DFFFFFF);
        colorWheel.setColor(currentColor);
        
        final View preview = new View(this);
        android.widget.LinearLayout.LayoutParams previewParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 120);
        previewParams.setMargins(0, 0, 0, 40);
        preview.setLayoutParams(previewParams);
        preview.setBackgroundColor(currentColor);
        
        colorWheel.setOnColorSelectedListener(color -> {
            // Apply 30% alpha (0x4D) to the selected color
            int colorWithAlpha = (0x4D << 24) | (color & 0x00FFFFFF);
            preview.setBackgroundColor(colorWithAlpha);
        });

        layout.addView(preview);
        layout.addView(colorWheel);

        builder.setView(layout);
        builder.setPositiveButton("Set Color", (dialog, which) -> {
            int finalColor = (0x4D << 24) | (colorWheel.getSelectedColor() & 0x00FFFFFF);
            saveColor(finalColor);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
