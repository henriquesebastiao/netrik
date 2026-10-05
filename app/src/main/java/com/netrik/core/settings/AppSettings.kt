package com.netrik.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Light/dark choice: follow the system (default) or force one of them. */
enum class ThemeMode { System, Light, Dark }

/** Appearance settings chosen on the Settings screen. */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** Wallpaper colors (Material You) on Android 12+; off uses the Netrik scheme. */
    val dynamicColor: Boolean = true,
    /** Pure black background when the dark theme is in use. */
    val amoled: Boolean = false,
)

/** Network-related settings, read by the hub and the Wi-Fi tab; an interface so they can be faked in tests. */
interface NetworkPreferences {
    /** Look up the public IP automatically (on every network change) instead of on demand. */
    val alwaysShowPublicIp: Flow<Boolean>

    /** Leave networks that don't broadcast their name out of the Wi-Fi tab. */
    val hideHiddenWifi: Flow<Boolean>
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Stores the settings with DataStore. The app language lives in [AppLanguages]. */
@Singleton
class SettingsRepository @Inject constructor(@param:ApplicationContext private val context: Context) : NetworkPreferences {

    val appearance: Flow<AppearanceSettings> = context.settingsStore.data.map { prefs ->
        AppearanceSettings(
            themeMode = prefs[THEME_MODE]?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } } ?: ThemeMode.System,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
            amoled = prefs[AMOLED] ?: false,
        )
    }

    override val alwaysShowPublicIp: Flow<Boolean> = context.settingsStore.data.map { it[ALWAYS_PUBLIC_IP] ?: false }
    override val hideHiddenWifi: Flow<Boolean> = context.settingsStore.data.map { it[HIDE_HIDDEN_WIFI] ?: false }

    suspend fun setAlwaysShowPublicIp(enabled: Boolean) = context.settingsStore.edit { it[ALWAYS_PUBLIC_IP] = enabled }
    suspend fun setHideHiddenWifi(enabled: Boolean) = context.settingsStore.edit { it[HIDE_HIDDEN_WIFI] = enabled }

    suspend fun setThemeMode(mode: ThemeMode) = context.settingsStore.edit { it[THEME_MODE] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.settingsStore.edit { it[DYNAMIC_COLOR] = enabled }
    suspend fun setAmoled(enabled: Boolean) = context.settingsStore.edit { it[AMOLED] = enabled }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AMOLED = booleanPreferencesKey("amoled")
        val ALWAYS_PUBLIC_IP = booleanPreferencesKey("always_public_ip")
        val HIDE_HIDDEN_WIFI = booleanPreferencesKey("hide_hidden_wifi")
    }
}
