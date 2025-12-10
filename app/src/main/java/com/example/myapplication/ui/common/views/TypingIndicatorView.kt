package com.example.myapplication.ui.common.views

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.myapplication.R

/**
 * 正在生成中的三点跳动动画视图
 * 
 * 用于在 AI 回复生成过程中显示加载状态
 */
class TypingIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val dots = mutableListOf<View>()
    private var animatorSet: AnimatorSet? = null
    
    companion object {
        private const val DOT_COUNT = 3
        private const val DOT_SIZE_DP = 8
        private const val DOT_MARGIN_DP = 4
        private const val ANIMATION_DURATION = 400L
        private const val ANIMATION_DELAY = 150L
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        
        val dotSize = (DOT_SIZE_DP * resources.displayMetrics.density).toInt()
        val dotMargin = (DOT_MARGIN_DP * resources.displayMetrics.density).toInt()
        
        // 创建三个圆点
        repeat(DOT_COUNT) { index ->
            val dot = View(context).apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_typing_dot)
                alpha = 0.4f
            }
            
            val params = LayoutParams(dotSize, dotSize).apply {
                if (index > 0) {
                    marginStart = dotMargin
                }
            }
            
            addView(dot, params)
            dots.add(dot)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == View.VISIBLE) {
            startAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (visibility == View.VISIBLE) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }

    /**
     * 启动跳动动画
     */
    private fun startAnimation() {
        // 如果动画已经在运行，不重复启动
        if (animatorSet?.isRunning == true) return
        
        animatorSet?.cancel()
        
        val animators = dots.mapIndexed { index, dot ->
            // 透明度动画
            val alphaAnimator = ObjectAnimator.ofFloat(dot, "alpha", 0.4f, 1f, 0.4f).apply {
                duration = ANIMATION_DURATION
                repeatCount = ObjectAnimator.INFINITE
                startDelay = index * ANIMATION_DELAY
            }
            
            // 缩放动画 Y
            val scaleYAnimator = ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.3f, 1f).apply {
                duration = ANIMATION_DURATION
                repeatCount = ObjectAnimator.INFINITE
                startDelay = index * ANIMATION_DELAY
            }
            
            // 缩放动画 X
            val scaleXAnimator = ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.3f, 1f).apply {
                duration = ANIMATION_DURATION
                repeatCount = ObjectAnimator.INFINITE
                startDelay = index * ANIMATION_DELAY
            }
            
            listOf(alphaAnimator, scaleYAnimator, scaleXAnimator)
        }.flatten()
        
        animatorSet = AnimatorSet().apply {
            playTogether(animators)
            start()
        }
    }

    /**
     * 停止动画
     */
    private fun stopAnimation() {
        animatorSet?.cancel()
        animatorSet = null
        
        // 重置所有圆点状态
        dots.forEach { dot ->
            dot.alpha = 0.4f
            dot.scaleX = 1f
            dot.scaleY = 1f
        }
    }
}
