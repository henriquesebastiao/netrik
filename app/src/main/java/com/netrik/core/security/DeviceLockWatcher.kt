package com.netrik.core.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Locks the app when the device screen turns off, which is when the device locks. Locking on
 * screen off (instead of on unlock) means the app content never flashes before the lock screen.
 * Registered for the whole process; a new process starts locked anyway.
 */
object DeviceLockWatcher {
    fun register(context: Context, manager: AppLockManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) manager.lock()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
