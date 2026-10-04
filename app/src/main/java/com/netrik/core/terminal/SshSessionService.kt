package com.netrik.core.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.netrik.MainActivity
import com.netrik.R
import com.netrik.core.settings.AppLanguages
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service while there is an open SSH session: without it Android drops the app's
 * network shortly after it goes to the background. The notification lists the sessions and offers "Disconnect all".
 */
@AndroidEntryPoint
class SshSessionService : Service() {

    @Inject lateinit var manager: SshSessionManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    /** Android 12 and older: notification texts in the language chosen in Settings. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguages.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT_ALL) manager.closeAll()
        // startForegroundService requires startForeground right away, even if the list is already empty.
        promote(manager.sessions.value)
        if (!started) {
            started = true
            scope.launch {
                manager.sessions.collect { sessions ->
                    if (sessions.isEmpty()) {
                        ServiceCompat.stopForeground(this@SshSessionService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        promote(sessions)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun promote(sessions: List<SshTerminal>) {
        ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(sessions), type)
    }

    private fun notification(sessions: List<SshTerminal>): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnect = PendingIntent.getService(
            this, 1,
            Intent(this, SshSessionService::class.java).setAction(ACTION_DISCONNECT_ALL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val count = sessions.size.coerceAtLeast(1)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentTitle(resources.getQuantityString(R.plurals.ssh_notification_title, count, count))
            .setContentText(sessions.joinToString(" · ") { it.name })
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_link_off, getString(R.string.ssh_notification_disconnect_all), disconnect)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "ssh_sessions"
        private const val NOTIFICATION_ID = 22
        private const val ACTION_DISCONNECT_ALL = "com.netrik.action.SSH_DISCONNECT_ALL"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.ssh_notification_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
