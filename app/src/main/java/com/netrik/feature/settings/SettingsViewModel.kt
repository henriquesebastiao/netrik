package com.netrik.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.security.AppLockManager
import com.netrik.core.security.AppLockState
import com.netrik.core.security.PinCheck
import com.netrik.core.settings.AppearanceSettings
import com.netrik.core.settings.SettingsRepository
import com.netrik.core.settings.ThemeMode
import com.netrik.feature.applock.PinFlowActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Appearance settings. The language is read and changed directly through AppLanguages (it needs the Activity). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val appLock: AppLockManager,
) : ViewModel(), PinFlowActions {

    val lock: StateFlow<AppLockState> = appLock.state

    val alwaysShowPublicIp: StateFlow<Boolean> = settings.alwaysShowPublicIp.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val hideHiddenWifi: StateFlow<Boolean> = settings.hideHiddenWifi.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val identifyDevicesByPorts: StateFlow<Boolean> = settings.identifyDevicesByPorts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setAlwaysShowPublicIp(enabled: Boolean) {
        viewModelScope.launch { settings.setAlwaysShowPublicIp(enabled) }
    }

    fun setHideHiddenWifi(enabled: Boolean) {
        viewModelScope.launch { settings.setHideHiddenWifi(enabled) }
    }

    fun setIdentifyDevicesByPorts(enabled: Boolean) {
        viewModelScope.launch { settings.setIdentifyDevicesByPorts(enabled) }
    }

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

    fun setBiometric(enabled: Boolean) {
        viewModelScope.launch { appLock.setBiometric(enabled) }
    }

    override suspend fun confirm(pin: String): PinCheck = appLock.confirm(pin)
    override suspend fun enable(pin: String) = appLock.enable(pin)
    override suspend fun changePin(pin: String) = appLock.changePin(pin)
    override suspend fun disable(pin: String): PinCheck = appLock.disable(pin)
}
