package com.netrik.feature.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.security.AppLockManager
import com.netrik.core.security.AppLockState
import com.netrik.core.security.PinCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lock screen: typed PIN, last result and the unlock actions. */
@HiltViewModel
class AppLockViewModel @Inject constructor(private val manager: AppLockManager) : ViewModel() {

    val lock: StateFlow<AppLockState> = manager.state

    private val _pin = MutableStateFlow("")
    val pin: StateFlow<String> = _pin.asStateFlow()

    private val _result = MutableStateFlow<PinCheck?>(null)
    val result: StateFlow<PinCheck?> = _result.asStateFlow()

    /** Bumped on every wrong attempt, so the dots shake again even with the same message. */
    private val _errorKey = MutableStateFlow(0)
    val errorKey: StateFlow<Int> = _errorKey.asStateFlow()

    fun onPinChange(value: String) {
        _pin.value = value
    }

    fun submit(pin: String) {
        viewModelScope.launch {
            val result = manager.unlockWithPin(pin)
            _pin.value = ""
            _result.value = if (result == PinCheck.Correct) null else result
            if (result != PinCheck.Correct) _errorKey.value++
        }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            manager.unlockWithBiometric()
            _pin.value = ""
            _result.value = null
        }
    }
}
