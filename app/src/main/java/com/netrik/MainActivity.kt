package com.netrik

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import com.netrik.core.security.AppLockManager
import com.netrik.feature.applock.LockScreen
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.settings.AppLanguages
import com.netrik.core.settings.SettingsRepository
import com.netrik.core.settings.ThemeMode
import com.netrik.navigation.NetrikApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
// FragmentActivity (not just ComponentActivity): the system biometric prompt needs it.
class MainActivity : FragmentActivity() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var appLock: AppLockManager

    /** Android 12 and older: applies the language chosen in Settings (13+ uses the system per-app language). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguages.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A tiny local file: reading it before the first frame avoids flashing the wrong theme.
        val initial = runBlocking { settings.appearance.first() }
        // Same for the app lock: the first frame must already be the lock screen when it's on.
        runBlocking { appLock.load() }
        setContent {
            val appearance by settings.appearance.collectAsStateWithLifecycle(initial)
            val dark = when (appearance.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // System bar icons follow the app theme, which may differ from the system one.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose { }
            }
            val lock by appLock.state.collectAsStateWithLifecycle()
            LaunchedEffect(lock.locked) {
                // The terminal keyboard (a plain View) may still be open behind the lock screen.
                if (lock.locked) getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            }
            NetrikTheme(darkTheme = dark, dynamicColor = appearance.dynamicColor, amoled = appearance.amoled) {
                Box {
                    // The app stays composed under the lock (navigation and running tools are kept),
                    // but it's hidden from accessibility services while locked.
                    Box(if (lock.locked) Modifier.clearAndSetSemantics { } else Modifier) { NetrikApp() }
                    // The lock is a dialog window: it takes the focus, so the keyboard and hardware keys
                    // can't reach the screens behind it (e.g. the SSH terminal).
                    if (lock.locked) LockScreen()
                }
            }
        }
    }

    private companion object {
        // Same scrims enableEdgeToEdge() uses by default for 3-button navigation.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
