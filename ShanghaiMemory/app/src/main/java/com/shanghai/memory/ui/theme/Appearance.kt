package com.shanghai.memory.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import com.shanghai.memory.data.AppSettings
import com.shanghai.memory.data.SettingsStore

/**
 * 把 DataStore 里的外观配置换算成 [ThemeState]。
 * 入口只有两处：进程启动时（App.onCreate）与设置页改动后。
 */
object Appearance {

    /** 系统当前是否暗色 */
    fun systemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun applyFrom(settings: AppSettings, dark: Boolean) {
        val light = if (settings.followSystem) !dark else settings.lightMode
        // 只有选中「自定义」才认主色；预设色板自带的强调色优先
        val palette = PaletteId.of(settings.paletteId)
        val accent = if (palette == PaletteId.CUSTOM) {
            settings.customAccent.takeIf { it.isNotBlank() }
                ?.let { runCatching { Color.parseColor(it) }.getOrNull() }
        } else {
            null
        }
        ThemeState.apply(
            palette = palette,
            light = light,
            customAccent = accent,
            font = FontId.of(settings.fontId),
            fontScale = settings.fontScale,
            importedFontPath = settings.importedFontPath,
            cornerDp = settings.cornerDp,
            surfaceAlpha = settings.surfaceAlpha
        )
    }

    /** 同步读一次落盘配置并生效，只在 Application 启动时调用 */
    suspend fun pushToState(context: Context) {
        applyFrom(SettingsStore(context).current(), systemDark(context))
    }

    /** 把颜色转成 #AARRGGBB 文本，写回 DataStore 用 */
    fun toHex(color: Int): String = String.format("#%08X", color)
}
