package tech.zakomi.zakdroid.terminal

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Foreground service that exists while at least one terminal session is running, so that
 * aggressive battery managers (e.g. Honor MagicOS) do not kill builds and programs when the
 * user leaves the app. It holds no state: the sessions live in [TerminalSessionManager].
 */
class TerminalKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The latest count, not the one in the intent: a short program may already have ended.
        val count = currentCount
        // After startForegroundService(), Android 12+ crashes the app unless startForeground()
        // is called, even if the service only wants to stop again. So always call it first.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(this, count.coerceAtLeast(1)), type)
            running = count > 0
        } catch (e: Exception) {
            Log.w(TAG, "cannot start foreground service", e)
            running = false
        }
        if (!running) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        // Sessions do not survive process death, so there is nothing to restart.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TerminalKeepAlive"
        private const val CHANNEL_ID = "terminal-sessions"
        private const val NOTIFICATION_ID = 4202

        @Volatile
        private var running = false

        @Volatile
        private var currentCount = 0

        /** Called by [TerminalSessionManager] whenever the number of running sessions changes. */
        fun update(context: Context, runningSessions: Int) {
            val app = context.applicationContext
            currentCount = runningSessions
            when {
                runningSessions <= 0 -> if (running) app.stopService(Intent(app, TerminalKeepAliveService::class.java))
                // Already in the foreground: only refresh the notification (no new service start from the background).
                running -> app.getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification(app, runningSessions))
                // Only start while the app is visible: Android 12+ forbids starting a foreground
                // service from the background. Sessions are only opened from the UI anyway.
                !isAppVisible() -> Log.i(TAG, "not starting keep-alive: app is in the background")
                else -> try {
                    ContextCompat.startForegroundService(
                        app,
                        Intent(app, TerminalKeepAliveService::class.java),
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "cannot start keep-alive service", e)
                }
            }
        }

        private fun isAppVisible(): Boolean =
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

        private fun notification(context: Context, count: Int): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.terminal_notification_channel), NotificationManager.IMPORTANCE_LOW),
                )
            }
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_terminal)
                .setContentTitle(context.resources.getQuantityString(R.plurals.terminal_notification_title, count, count))
                .setContentText(context.getString(R.string.terminal_notification_text))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }
    }
}
