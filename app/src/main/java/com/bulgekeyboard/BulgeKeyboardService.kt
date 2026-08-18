package com.bulgekeyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager

class BulgeKeyboardService : InputMethodService() {

    companion object {
        @JvmField
        var instance: BulgeKeyboardService? = null
    }

    private var keyboardView: BulgeKeyboardView? = null

    override fun onCreateInputView(): View {
        instance = this
        val view = BulgeKeyboardView(this)
        view.onKeyPressed = {
            currentInputConnection?.commitText(it, 1)
            // If the user types a specific "done" character or we want it to close
        }
        keyboardView = view
        // Keep the screen on while the keyboard is active
        window.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        return view
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView?.refreshSettings()
    }

    // 🔥 This is now only a fallback; MyAccessibilityService handles primary buttons
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!isInputViewShown) return
        outInsets.contentTopInsets = outInsets.visibleTopInsets
    }

    fun typeFocused() {
        keyboardView?.typeCurrentKey(false)
    }

    fun typeSecondary() {
        keyboardView?.typeCurrentKey(true)
    }

    fun handlePowerDoubleClick() {
        keyboardView?.onPowerDoubleClick()
    }

    fun handleEnter() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        requestHideSelf(0)
    }

    fun hideSelf() {
        requestHideSelf(0)
    }

    fun handleBackspace() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }
}