package com.netrik.core.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Languages the app ships. [System] follows the device language when Netrik has it (English otherwise). */
enum class AppLanguage(val tag: String?) {
    System(null),
    English("en"),
    PortugueseBrazil("pt-BR"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag != null && tag != null && Locale.forLanguageTag(it.tag).language == Locale.forLanguageTag(tag).language }
                ?: System
    }
}

/**
 * Per-app language. On Android 13+ it uses the system per-app language (also shown in the system
 * settings, see `res/xml/locales_config.xml`); on older versions the choice is saved in
 * SharedPreferences and applied by wrapping each Activity/Service context ([wrap]).
 */
object AppLanguages {
    private const val PREFS = "app_language"
    private const val KEY = "tag"

    fun current(context: Context): AppLanguage =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) AppLanguage.System else AppLanguage.fromTag(locales[0].toLanguageTag())
        } else {
            AppLanguage.fromTag(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
        }

    /** Saves the choice. On Android 13+ the system recreates the activities; before that, call `recreate()`. */
    fun set(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.tag).apply()
        }
    }

    /** Device language, regardless of the app choice. */
    fun systemLocale(context: Context): Locale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).systemLocales[0]
        } else {
            Resources.getSystem().configuration.locales[0]
        }

    /** True when the device language has translations in the app (otherwise the app shows English). */
    fun isSupported(locale: Locale): Boolean = AppLanguage.entries.any { it.tag != null && Locale.forLanguageTag(it.tag).language == locale.language }

    /** Android 12 and older: applies the saved language to an Activity/Service base context. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = current(base).tag ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }
}
