package com.sublingo.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.sublingo.app.MainActivity

object BackgroundWorkNotifications {
    const val CHANNEL_ID = "background_media_work"
    private const val CHANNEL_NAME = "后台下载与视频处理"
    private const val COMPLETION_CHANNEL_ID = "video_processing_completion"
    private const val COMPLETION_CHANNEL_NAME = "视频处理完成"

    fun foregroundInfo(
        context: Context,
        notificationId: Int,
        title: String,
        text: String,
        progress: Int? = null,
        cancelIntent: PendingIntent? = null,
    ): ForegroundInfo {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW))
        }
        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (progress == null) builder.setProgress(0, 0, true) else builder.setProgress(100, progress.coerceIn(0, 100), false)
        cancelIntent?.let { builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", it) }
        val notification = builder.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(notificationId, notification)
    }

    fun notifyVideoProcessingCompleted(
        context: Context,
        videoId: String,
        videoTitle: String?,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    COMPLETION_CHANNEL_ID,
                    COMPLETION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val notificationId = completionNotificationId(videoId)
        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, COMPLETION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("视频处理完成")
            .setContentText(completionNotificationText(videoTitle))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // A deterministic tag and ID make a repeated completion update replace the
        // previous notification instead of creating several cards for one video.
        notifications.notify("video-processing-$videoId", notificationId, notification)
    }

    internal fun completionNotificationText(videoTitle: String?): String {
        val title = videoTitle?.trim().orEmpty()
        return if (title.isBlank()) {
            "字幕、翻译和生词已生成"
        } else {
            "《$title》的字幕、翻译和生词已生成"
        }
    }

    internal fun completionNotificationId(videoId: String): Int =
        50_000 + (videoId.hashCode() and 0x3fff)
}
