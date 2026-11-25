package com.shanghai.memory.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.shanghai.memory.R

/**
 * 配色预设。真正生效的静态色值在 res/values/colors.xml 与 themes.xml 的 sh* 令牌里，
 * 这里只负责 id、展示信息与主题壳的映射。
 *
 * 每套色板都有暗版与亮版两套 style：静态主题只用来给控件一个「初始色」，
 * 用户一旦自定义主色，最终颜色由 [PaletteColors.derive] 现算、再由 [ThemeEngine] 覆盖。
 */
enum class PaletteId(
    val key: String,
    val label: String,
    val blurb: String,
    val darkStyleRes: Int,
    val lightStyleRes: Int,
    val swatchColorRes: Int
) {
    INK_GOLD(
        key = "ink_gold",
        label = "墨金",
        blurb = "暖金压在墨黑上，最接近工业档案的纸面感",
        darkStyleRes = R.style.Theme_ShanghaiMemory,
        lightStyleRes = R.style.Theme_ShanghaiMemory_Light,
        swatchColorRes = R.color.amber
    ),
    SAKURA(
        key = "sakura",
        label = "樱花粉",
        blurb = "深樱墨底配樱粉高光，夜里读故事不刺眼",
        darkStyleRes = R.style.Theme_ShanghaiMemory_Sakura,
        lightStyleRes = R.style.Theme_ShanghaiMemory_Sakura_Light,
        swatchColorRes = R.color.sakura_accent
    ),
    CELADON(
        key = "celadon",
        label = "青瓷",
        blurb = "青釉般的冷绿，配滨江和厂房的水汽",
        darkStyleRes = R.style.Theme_ShanghaiMemory_Celadon,
        lightStyleRes = R.style.Theme_ShanghaiMemory_Celadon_Light,
        swatchColorRes = R.color.celadon_accent
    ),

    /**
     * 自定义：静态主题用墨金兜底，真正的颜色由用户挑的主色现算。
     * 只有选中这一项，「主色」设置项才可点——预设色板自带强调色，再让用户覆盖只会打架。
     */
    CUSTOM(
        key = "custom",
        label = "自定义",
        blurb = "自己挑一个主色，背景、卡片、文字按它的色相自动派生",
        darkStyleRes = R.style.Theme_ShanghaiMemory,
        lightStyleRes = R.style.Theme_ShanghaiMemory_Light,
        swatchColorRes = R.color.amber
    );

    companion object {
        fun of(key: String?): PaletteId = entries.firstOrNull { it.key == key } ?: INK_GOLD
    }
}

/** 正文字体。前四项走系统字体族，其余是随包带的字体或用户自己导进来的文件 */
enum class FontId(
    val key: String,
    val label: String,
    val blurb: String,
    val resId: Int = 0
) {
    SYSTEM_SANS("system_sans", "系统黑体", "手机默认的思源黑体，最稳"),
    SYSTEM_SERIF("system_serif", "系统宋体", "老派衬线，读长故事最像书"),
    SYSTEM_MONO("system_mono", "系统等宽", "字符等宽，适合看资料与代码"),
    MAPLE_MONO("maple_mono", "JetBrains Maple Mono", "随包带的等宽字体，中文与代码兼顾", R.font.jetbrains_maple_mono),
    IMPORTED("imported", "我自己导入的字体", "从手机文件里选一个 .ttf 或 .otf");

    companion object {
        fun of(key: String?): FontId = entries.firstOrNull { it.key == key } ?: SYSTEM_SANS
    }
}

/**
 * 进程内共享的当前外观。Activity 的 onCreate 发生在任何协程回调之前，
 * 所以这里必须是同步可读的状态，不能挂在 DataStore 的 Flow 后面。
 */
object ThemeState {

    @Volatile
    var palette: PaletteId = PaletteId.INK_GOLD
        private set

    /** true 表示亮色 */
    @Volatile
    var light: Boolean = false
        private set

    /** 用户自定义主色后的派生色板；为空表示直接用预设 */
    @Volatile
    var custom: PaletteColors? = null
        private set

    @Volatile
    var font: FontId = FontId.SYSTEM_SANS
        private set

    @Volatile
    var fontScale: Float = 1.0f
        private set

    /** 用户导入的字体绝对路径（FontId.IMPORTED 时用） */
    @Volatile
    var importedFontPath: String = ""
        private set

    @Volatile
    var revision: Int = 0
        private set

    /** 最终生效的色板 */
    val colors: PaletteColors
        get() = custom ?: PaletteColors.of(palette, light)

    val styleRes: Int
        get() = if (light) palette.lightStyleRes else palette.darkStyleRes

    /** 用到的圆角半径（dp），自定义配色时会跟着变 */
    @Volatile
    var cornerDp: Int = 18
        private set

    /** 背景层不透明度百分比：40–100，低于 100 会透出底下的地图 */
    @Volatile
    var surfaceAlpha: Int = 100
        private set

    private var lastSignature: String = ""

    /**
     * 一次性写入全部外观状态。只有真正变化时才让 [revision] 自增，
     * 否则每个 Activity 的 onResume 都会触发 recreate() 无限循环。
     */
    fun apply(
        palette: PaletteId,
        light: Boolean,
        customAccent: Int?,
        font: FontId,
        fontScale: Float,
        importedFontPath: String,
        cornerDp: Int,
        surfaceAlpha: Int = 100
    ) {
        val signature =
            "$palette|$light|$customAccent|$font|$fontScale|$importedFontPath|$cornerDp|$surfaceAlpha"
        this.palette = palette
        this.light = light
        this.custom = customAccent?.let { PaletteColors.derive(it, !light) }
        this.font = font
        this.fontScale = fontScale.coerceIn(0.85f, 1.45f)
        this.importedFontPath = importedFontPath
        this.cornerDp = cornerDp.coerceIn(0, 28)
        this.surfaceAlpha = surfaceAlpha.coerceIn(40, 100)
        if (signature != lastSignature) {
            lastSignature = signature
            revision++
        }
    }

    /** 兼容旧调用：只换色板，其它不变（surfaceAlpha 必须原样带上，否则不透明度会被重置成 100） */
    fun update(id: PaletteId) {
        apply(palette = id, light = light, customAccent = null, font = font,
            fontScale = fontScale, importedFontPath = importedFontPath, cornerDp = cornerDp,
            surfaceAlpha = surfaceAlpha)
    }

    /** 当前正文字体；取不到就返回 null 让调用方用系统默认 */
    fun typeface(cache: FontCache): Typeface? = cache.get(font, importedFontPath)
}

/** 字体缓存：createFromFile 与 getFont 都会读盘，每次 bind 都调用必须缓存 */
class FontCache(private val context: Context) {

    private val cache = HashMap<String, Typeface?>()

    fun get(font: FontId, importedPath: String): Typeface? {
        val key = if (font == FontId.IMPORTED) "file:$importedPath" else font.key
        return cache.getOrPut(key) {
            runCatching {
                when (font) {
                    FontId.SYSTEM_SANS -> Typeface.SANS_SERIF
                    FontId.SYSTEM_SERIF -> Typeface.SERIF
                    FontId.SYSTEM_MONO -> Typeface.MONOSPACE
                    FontId.MAPLE_MONO -> ResourcesCompat.getFont(context, FontId.MAPLE_MONO.resId)
                    FontId.IMPORTED ->
                        if (importedPath.isNotBlank() && java.io.File(importedPath).isFile) {
                            Typeface.createFromFile(importedPath)
                        } else {
                            null
                        }
                }
            }.getOrNull()
        }
    }

    /** 导入新字体后必须清缓存，否则仍拿到旧的 Typeface */
    fun clear() = cache.clear()
}
