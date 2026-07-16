package com.pranavkd.instadown.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pranavkd.instadown.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "download_progress"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "com.pranavkd.instadown.action.PAUSE"
        const val ACTION_CANCEL = "com.pranavkd.instadown.action.CANCEL"
        const val EXTRA_GROUP_ID = "group_id"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Download Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows active download progress"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showProgress(
        groupId: Long,
        title: String,
        progress: Int,
        speedText: String,
        isIndeterminate: Boolean = false
    ) {
        val pauseIntent = PendingIntent.getBroadcast(
            context,
            groupId.toInt(),
            Intent(ACTION_PAUSE).putExtra(EXTRA_GROUP_ID, groupId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = PendingIntent.getBroadcast(
            context,
            groupId.toInt() + 10000,
            Intent(ACTION_CANCEL).putExtra(EXTRA_GROUP_ID, groupId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("$progress% - $speedText")
            .setProgress(100, progress, isIndeterminate)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID + groupId.toInt(), notification)
    }

    fun showFinalizing(groupId: Long, title: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText("Finalizing…")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + groupId.toInt(), notification)
    }

    fun showComplete(groupId: Long, title: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText("Download complete")
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + groupId.toInt(), notification)
    }

    fun showFailed(groupId: Long, title: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText("Failed: $message")
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + groupId.toInt(), notification)
    }

    fun cancel(groupId: Long) {
        notificationManager.cancel(NOTIFICATION_ID + groupId.toInt())
    }
}
