package com.csm.market.ui.common

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.csm.market.core.error.CsmError
import com.csm.market.databinding.ViewUiStateBinding
import java.util.WeakHashMap

/**
 * 统一三态视图的渲染逻辑。
 *
 * 抽成扩展函数而不是自定义 View 的原因：布局仍然用 <include> 复用，
 * 页面拿到的是 ViewBinding（类型安全、无 findViewById），
 * 这里只负责「状态 → 可见性」的映射，测试和排查都很直接。
 */
fun ViewUiStateBinding.hideState() {
    root.isVisible = false
}

fun ViewUiStateBinding.showLoading() {
    root.isVisible = true
    stateLoading.isVisible = true
    stateEmpty.isVisible = false
    stateError.isVisible = false
}

fun ViewUiStateBinding.showEmpty(@StringRes titleRes: Int, @StringRes descRes: Int) {
    root.isVisible = true
    stateLoading.isVisible = false
    stateEmpty.isVisible = true
    stateError.isVisible = false
    tvStateEmptyTitle.setText(titleRes)
    tvStateEmptyDesc.setText(descRes)
}

/**
 * 错误态：按钮是否出现由 [CsmError.retryable] 决定，
 * 不可重试时把回调置空即可，不用在每个页面各写一遍判断。
 */
fun ViewUiStateBinding.showError(error: CsmError, onRetry: (() -> Unit)? = null) {
    root.isVisible = true
    stateLoading.isVisible = false
    stateEmpty.isVisible = false
    stateError.isVisible = true

    tvStateErrorTitle.setText(error.titleRes)
    tvStateErrorDesc.setText(error.messageRes)

    val canRetry = error.retryable && onRetry != null
    btnStateRetry.isVisible = canRetry
    btnStateRetry.setOnClickListener(if (canRetry) View.OnClickListener { onRetry?.invoke() } else null)
}

private val pulseAnimators = WeakHashMap<View, ValueAnimator>()

/**
 * 骨架屏的呼吸动画。
 *
 * 这里每个骨架块各自持有一个 ValueAnimator（WeakHashMap 弱引用，View 回收后自动释放），
 * 而不是用 ObjectAnimator 无限循环——无限循环的动画在 Fragment 销毁时很容易泄漏，
 * 显式 stop 才是可控的。
 */
fun View.startSkeletonPulse() {
    stopSkeletonPulse()
    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { anim ->
            val fraction = anim.animatedValue as Float
            // 0 → 0.5 淡出到 0.55，0.5 → 1 再淡回来，形成「呼吸」效果
            val wave = if (fraction <= 0.5f) fraction * 2f else (1f - fraction) * 2f
            alpha = 1f - 0.45f * wave
        }
        start()
    }
    pulseAnimators[this] = animator
}

fun View.stopSkeletonPulse() {
    pulseAnimators.remove(this)?.cancel()
    alpha = 1f
}
