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
    private val labelZh: String,
    private val labelEn: String,
    private val blurbZh: String,
    private val blurbEn: String,
    val darkStyleRes: Int,
    val lightStyleRes: Int,
    val swatchColorRes: Int
) {
    INK_GOLD(
        key = "ink_gold",
        labelZh = "墨金",
        labelEn = "Ink Gold",
        blurbZh = "暖金压在墨黑上，像翻开旧工业档案的纸页",
        blurbEn = "Warm gold on deep ink, like opening an archival ledger",
        darkStyleRes = R.style.Theme_ShanghaiMemory,
        lightStyleRes = R.style.Theme_ShanghaiMemory_Light,
        swatchColorRes = R.color.amber
    ),
    SAKURA(
        key = "sakura",
        labelZh = "樱花粉",
        labelEn = "Sakura Rose",
        blurbZh = "深樱底配柔粉高光，夜里慢慢读故事也不刺眼",
        blurbEn = "Dusky plum paired with soft sakura rose, easy on night walks",
        darkStyleRes = R.style.Theme_ShanghaiMemory_Sakura,
        lightStyleRes = R.style.Theme_ShanghaiMemory_Sakura_Light,
        swatchColorRes = R.color.sakura_accent
    ),
    CELADON(
        key = "celadon",
        labelZh = "青瓷",
        labelEn = "Celadon",
        blurbZh = "青釉般的温润冷绿，很衬江边和老厂房的水汽",
        blurbEn = "Porcelain sage green that suits the riverfront mist and old mills",
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
        labelZh = "自定义",
        labelEn = "Custom",
        blurbZh = "选一个你偏爱的主色，背景、卡片和字色都会跟着协调配好",
        blurbEn = "Pick one favorite accent color and the rest harmonizes automatically",
        darkStyleRes = R.style.Theme_ShanghaiMemory,
        lightStyleRes = R.style.Theme_ShanghaiMemory_Light,
        swatchColorRes = R.color.amber
    );

    val label: String get() = if (ThemeState.isEnglish) labelEn else labelZh
    val blurb: String get() = if (ThemeState.isEnglish) blurbEn else blurbZh

    companion object {
        fun of(key: String?): PaletteId = entries.firstOrNull { it.key == key } ?: INK_GOLD
    }
}

/** 正文字体。前四项走系统字体族，其余是随包带的字体或用户自己导进来的文件 */
enum class FontId(
    val key: String,
    private val labelZh: String,
    private val labelEn: String,
    private val blurbZh: String,
    private val blurbEn: String,
    val resId: Int = 0
) {
    SYSTEM_SANS("system_sans", "系统黑体", "System Sans", "手机自带的无衬线字体，清晰耐看", "Clean default sans-serif for everyday reading"),
    SYSTEM_SERIF("system_serif", "系统宋体", "System Serif", "带笔锋的衬线体，读长故事很有书卷气", "Classic serif that reads like a printed chronicle"),
    SYSTEM_MONO("system_mono", "系统等宽", "System Mono", "字符等宽，看年代、门牌和资料很利落", "Crisp monospace great for dates and records"),
    MAPLE_MONO("maple_mono", "JetBrains Maple Mono", "JetBrains Maple Mono", "内置等宽字体，中英文混排都很舒服", "Bundled monospace crafted for bilingual prose", R.font.jetbrains_maple_mono),
    IMPORTED("imported", "我导入的字体", "Imported Font", "从手机里挑选喜欢的 .ttf 或 .otf 字体文件", "Use your own .ttf or .otf font file");

    val label: String get() = if (ThemeState.isEnglish) labelEn else labelZh
    val blurb: String get() = if (ThemeState.isEnglish) blurbEn else blurbZh

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

    /** 界面与讲述语言："zh" 或 "en" */
    @Volatile
    var language: String = "zh"
        private set

    val isEnglish: Boolean get() = language == "en"

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
        surfaceAlpha: Int = 100,
        language: String = this.language
    ) {
        val lang = if (language == "en") "en" else "zh"
        val signature =
            "$palette|$light|$customAccent|$font|$fontScale|$importedFontPath|$cornerDp|$surfaceAlpha|$lang"
        this.palette = palette
        this.light = light
        this.custom = customAccent?.let { PaletteColors.derive(it, !light) }
        this.font = font
        this.fontScale = fontScale.coerceIn(0.85f, 1.45f)
        this.importedFontPath = importedFontPath
        this.cornerDp = cornerDp.coerceIn(0, 28)
        this.surfaceAlpha = surfaceAlpha.coerceIn(40, 100)
        this.language = lang
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
