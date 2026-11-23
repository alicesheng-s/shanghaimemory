package com.shanghai.memory.ui.theme

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 色板令牌在运行时的形态。
 *
 * 为什么要有这个类：XML 主题只能引用静态颜色资源，用户一旦想自己挑主色，
 * 静态资源就无能为力了（Android 没有在运行时生成 style 的能力）。
 * 所以静态主题只负责给控件一个「初始色」，真正的最终色在这里算，
 * 再由 [ThemeEngine] 覆盖到已经 inflate 出来的控件上。
 */
data class PaletteColors(
    val bg: Int,
    val bgRaise: Int,
    val card: Int,
    val line: Int,
    val accent: Int,
    val accentBright: Int,
    val text: Int,
    val textMuted: Int,
    val danger: Int,
    val ok: Int,
    val scrim: Int,
    val dark: Boolean
) {

    /** 控件当前颜色常常就是某个令牌，靠它把「原本的角色」找回来 */
    fun roleOf(color: Int): Role? = table[color]

    fun colorOf(role: Role): Int = when (role) {
        Role.BG -> bg
        Role.BG_RAISE -> bgRaise
        Role.CARD -> card
        Role.LINE -> line
        Role.ACCENT -> accent
        Role.ACCENT_BRIGHT -> accentBright
        Role.TEXT -> text
        Role.TEXT_MUTED -> textMuted
        Role.DANGER -> danger
        Role.OK -> ok
        Role.SCRIM -> scrim
    }

    /**
     * 压在 accent 上的文字色。
     *
     * 主色由用户随便挑，靛蓝这种中间调上既不能写黑字也不能写白字蒙，
     * 所以按相对亮度算一个：亮底给近黑，暗底给近白，对比度都在 4.5:1 以上。
     */
    val onAccent: Int =
        if (luminance(accent) > 0.52) 0xFF12100C.toInt() else 0xFFFFFDF8.toInt()

    private fun luminance(color: Int): Double {
        fun channel(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    /**
     * 反查表：把所有预设色板的每个令牌都登记进来。
     * 碰撞（两个角色同色）极少，先登记的优先，实测六套色板没有影响使用的碰撞。
     *
     * 必须是 lazy：PRESETS 里的元素本身就是 PaletteColors，构造它们时会走到这里，
     * 若在实例初始化阶段直接展开，PRESETS → 构造 → table → PRESETS 会无限递归爆栈。
     */
    private val table: Map<Int, Role> by lazy {
        buildMap {
            PRESETS.forEach { p ->
                putIfAbsent(p.bg, Role.BG)
                putIfAbsent(p.bgRaise, Role.BG_RAISE)
                putIfAbsent(p.card, Role.CARD)
                putIfAbsent(p.line, Role.LINE)
                putIfAbsent(p.accent, Role.ACCENT)
                putIfAbsent(p.accentBright, Role.ACCENT_BRIGHT)
                putIfAbsent(p.text, Role.TEXT)
                putIfAbsent(p.textMuted, Role.TEXT_MUTED)
                putIfAbsent(p.danger, Role.DANGER)
                putIfAbsent(p.ok, Role.OK)
                putIfAbsent(p.scrim, Role.SCRIM)
            }
        }
    }

    companion object {

        /** 六套预设的实际色值，必须与 res/values/colors.xml 保持一致 */
        val PRESETS: List<PaletteColors> by lazy {
            listOf(
                // 墨金 · 暗
                of("#0E1116", "#161A22", "#1C222C", "#2A3038", "#C8A66B", "#E8C88A",
                    "#F2EDE3", "#8A9099", "#D9534F", "#5FA87C", "#CC0E1116", dark = true),
                // 樱花粉 · 暗
                of("#1B1216", "#241820", "#2E1E28", "#432C38", "#F0A2B8", "#FFC8D8",
                    "#FBEFF3", "#B99CA7", "#E4677F", "#6FA98A", "#CC1B1216", dark = true),
                // 青瓷 · 暗
                of("#0F1614", "#16201D", "#1D2A25", "#2C3B35", "#8FBFA8", "#BBDCC9",
                    "#EFF5F1", "#8FA39A", "#D97A6E", "#7FB89A", "#CC0F1614", dark = true),
                // 墨金 · 亮
                of("#F7F3EC", "#FFFDF8", "#EFE8DC", "#DCD2C1", "#9A7B36", "#B08F45",
                    "#1A1712", "#6B6357", "#B3403C", "#3F7A57", "#CCF7F3EC", dark = false),
                // 樱花粉 · 亮
                of("#FBF0F3", "#FFF8FA", "#F6E4EA", "#E4CBD5", "#C35F7E", "#A94E6A",
                    "#241419", "#8A6472", "#C0455F", "#4E8A6B", "#CCFBF0F3", dark = false),
                // 青瓷 · 亮
                of("#F1F6F3", "#FBFEFC", "#E3EDE7", "#C9DAD1", "#4C7F68", "#3E6B57",
                    "#16211C", "#5E7268", "#A85A4F", "#3F7A5E", "#CCF1F6F3", dark = false)
            )
        }

        /** 预设：按色板与明暗取色 */
        fun of(palette: PaletteId, light: Boolean): PaletteColors {
            val index = when (palette) {
                PaletteId.INK_GOLD -> 0
                PaletteId.SAKURA -> 1
                PaletteId.CELADON -> 2
                // 自定义色板没有静态预设；用户还没挑色时按墨金的主色兜底，
                // 一旦挑过，ThemeState.colors 会直接走 derive 的结果，不会走到这里。
                PaletteId.CUSTOM -> return derive(0xFFC8A66B.toInt(), dark = !light)
            }
            return PRESETS[index + if (light) 3 else 0]
        }

        /**
         * 从主色派生整套令牌。
         *
         * 派生而不是让用户逐项填，是为了保证对比度：用户只要挑一个自己喜欢的主色，
         * 背景、卡片、描线、文字都按色相带一点点同一个味道，明暗关系由代码保证。
         */
        fun derive(accent: Int, dark: Boolean): PaletteColors {
            val hsl = toHsl(accent)
            val h = hsl[0]
            val s = hsl[1]
            val l = hsl[2]

            return if (dark) {
                val bg = fromHsl(h, 0.16, 0.055)
                val card = fromHsl(h, 0.14, 0.115)
                PaletteColors(
                    bg = bg,
                    bgRaise = fromHsl(h, 0.14, 0.085),
                    card = card,
                    line = fromHsl(h, 0.12, 0.21),
                    accent = accent,
                    accentBright = fromHsl(h, min(1.0, s * 0.85), min(0.88, l + 0.16)),
                    text = fromHsl(h, 0.20, 0.95),
                    textMuted = fromHsl(h, 0.10, 0.63),
                    danger = fromHsl(357.0, 0.62, 0.60),
                    ok = fromHsl(146.0, 0.32, 0.55),
                    scrim = Color.argb(0xCC, Color.red(bg), Color.green(bg), Color.blue(bg)),
                    dark = true
                )
            } else {
                val bg = fromHsl(h, 0.26, 0.968)
                PaletteColors(
                    bg = bg,
                    bgRaise = fromHsl(h, 0.30, 0.998),
                    card = fromHsl(h, 0.24, 0.928),
                    line = fromHsl(h, 0.16, 0.845),
                    accent = accent,
                    accentBright = fromHsl(h, min(1.0, s * 1.0), max(0.26, l - 0.12)),
                    text = fromHsl(h, 0.32, 0.13),
                    textMuted = fromHsl(h, 0.14, 0.42),
                    danger = fromHsl(357.0, 0.55, 0.44),
                    ok = fromHsl(146.0, 0.34, 0.34),
                    scrim = Color.argb(0xCC, Color.red(bg), Color.green(bg), Color.blue(bg)),
                    dark = false
                )
            }
        }

        private fun of(
            bg: String, bgRaise: String, card: String, line: String,
            accent: String, accentBright: String, text: String, textMuted: String,
            danger: String, ok: String, scrim: String, dark: Boolean
        ) = PaletteColors(
            bg = Color.parseColor(bg),
            bgRaise = Color.parseColor(bgRaise),
            card = Color.parseColor(card),
            line = Color.parseColor(line),
            accent = Color.parseColor(accent),
            accentBright = Color.parseColor(accentBright),
            text = Color.parseColor(text),
            textMuted = Color.parseColor(textMuted),
            danger = Color.parseColor(danger),
            ok = Color.parseColor(ok),
            scrim = Color.parseColor(scrim),
            dark = dark
        )

        /** 返回 [h(0-360), s(0-1), l(0-1)] */
        fun toHsl(color: Int): DoubleArray {
            val r = Color.red(color) / 255.0
            val g = Color.green(color) / 255.0
            val b = Color.blue(color) / 255.0
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val l = (maxC + minC) / 2.0
            if (maxC == minC) return doubleArrayOf(0.0, 0.0, l)
            val d = maxC - minC
            val s = if (l > 0.5) d / (2.0 - maxC - minC) else d / (maxC + minC)
            val h = when (maxC) {
                r -> ((g - b) / d + if (g < b) 6.0 else 0.0)
                g -> ((b - r) / d + 2.0)
                else -> ((r - g) / d + 4.0)
            } * 60.0
            return doubleArrayOf(h, s, l)
        }

        fun fromHsl(h: Double, s: Double, l: Double): Int {
            val hh = ((h % 360.0) + 360.0) % 360.0
            val ss = s.coerceIn(0.0, 1.0)
            val ll = l.coerceIn(0.0, 1.0)
            if (ss == 0.0) {
                val v = (ll * 255).toInt().coerceIn(0, 255)
                return Color.rgb(v, v, v)
            }
            val q = if (ll < 0.5) ll * (1 + ss) else ll + ss - ll * ss
            val p = 2 * ll - q
            fun channel(t: Double): Int {
                var tt = t
                if (tt < 0) tt += 1.0
                if (tt > 1) tt -= 1.0
                val value = when {
                    tt < 1.0 / 6 -> p + (q - p) * 6 * tt
                    tt < 1.0 / 2 -> q
                    tt < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - tt) * 6
                    else -> p
                }
                return (value * 255).toInt().coerceIn(0, 255)
            }
            return Color.rgb(
                channel(hh / 360.0 + 1.0 / 3),
                channel(hh / 360.0),
                channel(hh / 360.0 - 1.0 / 3)
            )
        }

        /** 两个颜色的感知距离，用来判断「差得够不够远」 */
        fun distance(a: Int, b: Int): Int =
            abs(Color.red(a) - Color.red(b)) +
                abs(Color.green(a) - Color.green(b)) +
                abs(Color.blue(a) - Color.blue(b))
    }
}

enum class Role { BG, BG_RAISE, CARD, LINE, ACCENT, ACCENT_BRIGHT, TEXT, TEXT_MUTED, DANGER, OK, SCRIM }
