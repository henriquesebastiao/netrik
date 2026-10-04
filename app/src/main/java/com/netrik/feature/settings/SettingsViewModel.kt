package com.netrik.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.settings.AppearanceSettings
import com.netrik.core.settings.SettingsRepository
import com.netrik.core.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Appearance settings. The language is read and changed directly through AppLanguages (it needs the Activity). */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {

    val appearance: StateFlow<AppearanceSettings> =
        settings.appearance.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun setAmoled(enabled: Boolean) {
        viewModelScope.launch { settings.setAmoled(enabled) }
    }
}
