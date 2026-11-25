package com.shanghai.memory.ui.theme

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import java.util.WeakHashMap

/**
 * 把用户配置的色板刷到已经 inflate 出来的控件上。
 *
 * 为什么不在 XML 里做：Android 没法在运行时生成 style 资源，而用户想自己挑主色。
 * 所以静态主题只负责给出「初始色」——那是某套预设色板的色值，控件初始化后
 * 身上带的颜色必然是预设表里的某一项，据此反查它原本想表达的角色，
 * 再换成当前最终色板里的同一个角色。布局文件一行都不用改。
 */
object ThemeEngine {

    private val baseTextSize = WeakHashMap<TextView, Float>()

    fun recolor(root: View, colors: PaletteColors = ThemeState.colors, font: Typeface? = null) {
        walk(root, colors, font)
    }

    private fun walk(view: View, colors: PaletteColors, font: Typeface?) {
        runCatching { recolorTextView(view, colors, font) }
        runCatching { recolorIcon(view, colors) }
        runCatching { recolorShape(view, colors) }
        runCatching { recolorButtonLabel(view, colors) }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walk(view.getChildAt(i), colors, font)
        }
    }

    /**
     * 实心按钮上的文字不能跟着角色走：主色一换，原本对比良好的深色字可能直接糊在按钮上。
     *
     * 只处理「底色就是主色」的实心按钮。描边按钮（返回、测试连通性）也带 backgroundTint，
     * 但那是透明的，若一并换成 onAccent，文字在浅底上会直接消失。
     */
    private fun recolorButtonLabel(view: View, colors: PaletteColors) {
        if (view !is TextView) return
        val tint = view.backgroundTintList ?: return
        if (colors.roleOf(tint.defaultColor) != Role.ACCENT) return
        if (view.textColors.defaultColor != colors.onAccent) {
            view.setTextColor(colors.onAccent)
        }
    }

    private fun recolorTextView(view: View, colors: PaletteColors, font: Typeface?) {
        if (view !is TextView) return
        // 色板预览这类控件必须保留自己的颜色，否则三套配色会被统一成当前主题色
        if (keepColor(view)) return
        val role = colors.roleOf(view.currentTextColor) ?: return
        view.setTextColor(colors.colorOf(role))
        if (themeProse(view)) {
            font?.let { view.typeface = it }
            // getTextSize 是 px、setTextSize(float) 是 sp，必须显式指定单位，否则字号会翻几倍
            val base = baseTextSize.getOrPut(view) { view.textSize }
            val target = base * ThemeState.fontScale
            if (kotlin.math.abs(view.textSize - target) > 0.5f) {
                view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, target)
            }
        }
    }

    /** 正文控件打 tag="sh:prose"，只有它们跟随用户选的字体与字号 */
    fun themeProse(view: View): Boolean = view.tag == TAG_PROSE

    /** 打 tag="sh:keep_color" 的控件保留自己在 XML 里写死的颜色 */
    fun keepColor(view: View): Boolean = view.tag == TAG_KEEP_COLOR

    private fun recolorIcon(view: View, colors: PaletteColors) {
        if (view !is ImageView) return
        val tint = view.imageTintList
        if (tint != null) {
            val role = colors.roleOf(tint.defaultColor) ?: return
            val next = colors.colorOf(role)
            if (next != tint.defaultColor) view.imageTintList = ColorStateList.valueOf(next)
            return
        }
        // 没有 ImageView 级 tint 的形象（例如 layer-list 画出来的光标）只能进 drawable 里换色
        val redrawn = recolorDrawable(view.drawable, colors)
        if (redrawn != null) view.setImageDrawable(redrawn)
    }

    /**
     * 递归处理复合 drawable。
     * layer-list 与 ripple 都是外壳，真正带颜色的是里层的 shape，只能钻进去改。
     */
    private fun recolorDrawable(drawable: Drawable?, colors: PaletteColors): Drawable? {
        when (drawable) {
            is GradientDrawable -> {
                val current = drawable.color?.defaultColor ?: return null
                val role = colors.roleOf(current) ?: return null
                drawable.setColor(colors.colorOf(role))
                return drawable
            }

            is LayerDrawable -> {
                for (i in 0 until drawable.numberOfLayers) {
                    recolorDrawable(drawable.getDrawable(i), colors)
                }
                return drawable
            }

            is RippleDrawable -> {
                recolorDrawable(drawable.findDrawableByLayerId(android.R.id.background), colors)
                recolorDrawable(drawable.findDrawableByLayerId(android.R.id.content), colors)
                return null
            }

            else -> return null
        }
    }

    /**
     * 背景改色。只处理能安全读回原色的两类：
     * shape 画出来的 GradientDrawable，以及 MaterialButton 的 backgroundTint。
     * 其它（RippleDrawable、九宫格）原样保留，宁可不动也不弄花。
     */
    private fun recolorShape(view: View, colors: PaletteColors) {
        // 上色时表面角色会被降不透明度，反查角色前先把 alpha 剥掉：
        // 否则第二次换色板时认不出这属于哪个角色，控件会停在旧色板上
        fun opaque(color: Int): Int = (color and 0x00FFFFFF) or 0xFF000000.toInt()

        val tintList = view.backgroundTintList
        if (tintList != null) {
            val role = colors.roleOf(opaque(tintList.defaultColor)) ?: return
            val next = colors.colorOf(role)
            if (next != tintList.defaultColor) view.backgroundTintList = ColorStateList.valueOf(next)
            return
        }
        val bg = view.background ?: return
        if (bg !is GradientDrawable) return
        val drawable = DrawableCompat.wrap(bg).mutate() as? GradientDrawable ?: return
        val current = drawable.color?.defaultColor ?: return
        val role = colors.roleOf(opaque(current)) ?: return
        drawable.setColor(withSurfaceAlpha(colors.colorOf(role), role))
        if (drawable.cornerRadius > 0f) {
            drawable.cornerRadius = view.resources.displayMetrics.density * ThemeState.cornerDp
        }
        view.background = drawable
    }

    /**
     * 让背景层透出底下的地图。
     * 只对三个表面角色生效——文字与强调色若被降低不透明度就成了不可读的噪点。
     */
    fun withSurfaceAlpha(color: Int, role: Role): Int {
        if (role != Role.BG && role != Role.BG_RAISE && role != Role.CARD) return color
        val percent = ThemeState.surfaceAlpha.coerceIn(40, 100)
        if (percent >= 100) return color
        val alpha = (255 * percent / 100f).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    const val TAG_PROSE = "sh:prose"

    /** 打了这个 tag 的控件不做颜色角色替换（色板预览块、品牌色等） */
    const val TAG_KEEP_COLOR = "sh:keep_color"
}
