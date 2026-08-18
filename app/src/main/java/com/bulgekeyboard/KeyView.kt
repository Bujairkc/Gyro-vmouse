package com.bulgekeyboard

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.res.ResourcesCompat
import kotlin.math.max

class KeyView(
    context: Context,
    val char: String,
    val typedChar: String,
    val secondary: String? = null,
    private val isSwitch: Boolean = false,
    private val emojiResId: Int? = null,
    private val useSystemEmoji: Boolean = false
) : FrameLayout(context) {

    private val keyBackgroundContainer: FrameLayout
    private val primaryTv = TextView(context)
    private val emojiIv = AppCompatImageView(context)
    private val secondaryTv = TextView(context)

    private val density = context.resources.displayMetrics.density

    private val boxWidth = (44 * density).toInt()
    private val boxHeight = (44 * density).toInt()

    val keyOuterWidth = (80 * density).toInt()
    val keyOuterHeight = (80 * density).toInt()

    private var glowAnimator: ValueAnimator? = null
    private var popAnimator: AnimatorSet? = null
    private var isGlowActive = false
    private var currentPhysicsScale = 1.0f

    var onKeyClick: (() -> Unit)? = null
    var onKeyLongClick: (() -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        clipChildren = false
        clipToPadding = false

        isClickable = true
        isLongClickable = true

        setOnClickListener { onKeyClick?.invoke() }
        setOnLongClickListener {
            onKeyLongClick?.invoke()
            true
        }

        layoutParams = LayoutParams(keyOuterWidth, keyOuterHeight)

        keyBackgroundContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }

        val innerWidth = if (isSwitch) (60 * density).toInt() else boxWidth

        keyBackgroundContainer.layoutParams = LayoutParams(
            innerWidth, boxHeight, Gravity.CENTER
        )

        val bg = GradientDrawable().apply {
            cornerRadius = 12 * density
            setColor(Color.WHITE)
            setStroke((1.5f * density).toInt(), Color.parseColor("#12000000"))
        }

        keyBackgroundContainer.background = bg

        val interMedium = try {
            if (useSystemEmoji) Typeface.DEFAULT else ResourcesCompat.getFont(context, R.font.inter_medium)
        } catch (e: Exception) {
            Typeface.DEFAULT_BOLD
        }

        if (emojiResId != null && !useSystemEmoji) {
            val emojiSize = (34 * density).toInt()
            emojiIv.setImageResource(emojiResId)
            emojiIv.setLayerType(LAYER_TYPE_HARDWARE, null)
            emojiIv.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            emojiIv.adjustViewBounds = true
            keyBackgroundContainer.addView(emojiIv, LayoutParams(emojiSize, emojiSize, Gravity.CENTER))

            emojiIv.post {
                emojiIv.drawable?.let { dr ->
                    dr.isFilterBitmap = true
                    if (dr is BitmapDrawable) {
                        dr.paint.apply {
                            isAntiAlias = true
                            isDither = true
                            isFilterBitmap = true
                            flags = flags or Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG
                        }
                    }
                }
            }
        } else {
            primaryTv.text = if (emojiResId != null && useSystemEmoji) typedChar else char
            primaryTv.setTextColor(if (isSwitch) Color.WHITE else Color.BLACK)
            primaryTv.typeface = interMedium
            primaryTv.gravity = Gravity.CENTER
            primaryTv.includeFontPadding = false
            
            // Fixed text size to fit box perfectly
            primaryTv.textSize = when {
                isSwitch -> 13f
                emojiResId != null && useSystemEmoji -> 32f
                else -> 22f // Clean, visible size for 44dp box
            }

            primaryTv.paint.apply {
                isSubpixelText = true
                isLinearText = true
                isFilterBitmap = true
                isAntiAlias = true
            }

            keyBackgroundContainer.addView(primaryTv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        if (secondary != null) {
            secondaryTv.text = secondary
            secondaryTv.textSize = 8f
            secondaryTv.setTextColor(Color.BLACK)
            secondaryTv.alpha = 0.6f
            secondaryTv.typeface = interMedium
            secondaryTv.includeFontPadding = false

            val secLp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            secLp.gravity = Gravity.TOP or Gravity.END
            secLp.topMargin = (4 * density).toInt()
            secLp.rightMargin = (5 * density).toInt()
            keyBackgroundContainer.addView(secondaryTv, secLp)
        }

        addView(keyBackgroundContainer)
    }

    private fun updateBackgroundAndBorder(bgColor: Int, borderColor: Int) {
        val gd = (keyBackgroundContainer.background as? GradientDrawable) ?: return
        gd.setColor(bgColor)
        gd.setStroke((1.5f * density).toInt(), borderColor)
    }

    fun setPhysics(
        scaleVal: Float,
        rotationVal: Float,
        opacityVal: Float,
        zIndexVal: Float,
        isLongPressing: Boolean = false,
        isCancelled: Boolean = false,
        isLastTyped: Boolean = false
    ) {
        currentPhysicsScale = scaleVal
        scaleX = scaleVal
        scaleY = scaleVal
        rotation = rotationVal
        alpha = max(0.92f, opacityVal)
        z = zIndexVal
        elevation = (zIndexVal / 5f) * density

        val bgColor = when {
            isLastTyped -> Color.parseColor("#E0F2FE")
            isCancelled -> Color.parseColor("#EF4444")
            isSwitch -> Color.parseColor("#0F172A")
            else -> Color.WHITE
        }

        val borderColor = when {
            isLongPressing -> Color.parseColor("#0095F6")
            else -> Color.parseColor("#14000000")
        }

        updateBackgroundAndBorder(bgColor, borderColor)
    }

    fun triggerPop() {
        popAnimator?.cancel()
        val targetPop = currentPhysicsScale * 1.4f
        
        val scaleXUp = ObjectAnimator.ofFloat(this, "scaleX", currentPhysicsScale, targetPop)
        val scaleYUp = ObjectAnimator.ofFloat(this, "scaleY", currentPhysicsScale, targetPop)
        val scaleXDown = ObjectAnimator.ofFloat(this, "scaleX", targetPop, currentPhysicsScale)
        val scaleYDown = ObjectAnimator.ofFloat(this, "scaleY", targetPop, currentPhysicsScale)

        scaleXUp.duration = 60
        scaleYUp.duration = 60
        scaleXDown.duration = 140
        scaleYDown.duration = 140

        popAnimator = AnimatorSet().apply {
            play(scaleXUp).with(scaleYUp)
            play(scaleXDown).with(scaleYDown).after(scaleXUp)
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Visual flash on pop
        updateBackgroundAndBorder(Color.parseColor("#BAE6FD"), Color.parseColor("#0095F6"))
        postDelayed({
            updateBackgroundAndBorder(if (isSwitch) Color.parseColor("#0F172A") else Color.WHITE, Color.parseColor("#14000000"))
        }, 200)
    }

    fun setGlow(active: Boolean) {
        if (isGlowActive == active) return
        isGlowActive = active
        glowAnimator?.cancel()

        if (active) {
            glowAnimator = ValueAnimator.ofFloat(1.0f, 1.15f).apply {
                duration = 400
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    val v = it.animatedValue as Float
                    primaryTv.scaleX = v
                    primaryTv.scaleY = v
                    emojiIv.scaleX = v
                    emojiIv.scaleY = v
                    
                    // Main character color change on glow
                    if (!isSwitch) {
                        primaryTv.setTextColor(Color.parseColor("#0095F6"))
                        updateBackgroundAndBorder(Color.WHITE, Color.parseColor("#0095F6"))
                    }
                }
                start()
            }
        } else {
            primaryTv.scaleX = 1f
            primaryTv.scaleY = 1f
            emojiIv.scaleX = 1f
            emojiIv.scaleY = 1f
            if (!isSwitch) {
                primaryTv.setTextColor(Color.BLACK)
                updateBackgroundAndBorder(Color.WHITE, Color.parseColor("#14000000"))
            }
        }
    }
}
