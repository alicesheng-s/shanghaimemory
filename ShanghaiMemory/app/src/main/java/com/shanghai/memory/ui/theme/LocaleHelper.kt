package com.shanghai.memory.ui.theme

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Wraps the Activity base context with the user's selected locale ("zh" or "en")
 * so XML strings and getString() resolve consistently on API 26–36.
 */
object LocaleHelper {

    fun localeFor(language: String): Locale =
        if (language == "en") Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE

    fun wrap(context: Context, language: String = ThemeState.language): Context {
        val locale = localeFor(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
