package com.netrik

import android.app.Application
import com.netrik.core.security.AppLockManager
import com.netrik.core.security.DeviceLockWatcher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NetrikApplication : Application() {

    @Inject lateinit var appLock: AppLockManager

    override fun onCreate() {
        super.onCreate()
        DeviceLockWatcher.register(this, appLock)
    }
}
