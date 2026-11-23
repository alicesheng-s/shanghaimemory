package com.shanghai.memory.ui.theme

import android.view.View
import com.shanghai.memory.R

/**
 * 过渡动画的唯一定义处。
 *
 * 现在只有「平移 + 淡入」这两种最朴素的动作，因为再复杂的动效在低端机上容易掉帧，
 * 而且这版的意义是把动效收口成一处的调用来——以后要换成共享元素或弹簧动画，
 * 只改这个对象，界面代码一行都不用动。
 */
object ThemeFx {

    /** 页内切换时长，与 res/anim 里的 duration 保持同一量级 */
    const val PAGE_DURATION = 200L

    /** 页内切换的位移距离（dp） */
    private const val PAGE_SHIFT_DP = 28f

    private fun px(view: View, dp: Float): Float = view.resources.displayMetrics.density * dp

    /** 淡入一个新页面，[forward] 决定从右边还是左边滑进来 */
    fun slideInPage(view: View, forward: Boolean) {
        view.visibility = View.VISIBLE
        view.animate().cancel()
        view.alpha = 0f
        view.translationX = px(view, if (forward) PAGE_SHIFT_DP else -PAGE_SHIFT_DP)
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(PAGE_DURATION)
            .start()
    }

    /** 把旧页面推出去，动画结束后才真正隐藏，避免提前 GONE 导致闪烁 */
    fun slideOutPage(view: View, forward: Boolean) {
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationX(px(view, if (forward) -PAGE_SHIFT_DP else PAGE_SHIFT_DP))
            .setDuration(PAGE_DURATION)
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
                view.translationX = 0f
            }
            .start()
    }

    /** Activity 进来：从右侧滑入 + 淡入 */
    fun activityIn() = R.anim.sh_slide_in_right to R.anim.sh_fade_out

    /** Activity 退出：淡入 + 向右滑出 */
    fun activityOut() = R.anim.sh_fade_in to R.anim.sh_slide_out_right

    /**
     * 列表项依次浮现。
     * 只对首屏有意义，重复调用会重播动画，所以调用方负责只在第一次显示时触发。
     */
    fun staggerIn(views: List<View>, step: Long = 45L) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.animate()
                .alpha(1f)
                .setStartDelay(index * step)
                .setDuration(220L)
                .start()
        }
    }
}
