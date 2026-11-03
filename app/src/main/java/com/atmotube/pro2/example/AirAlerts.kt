package com.atmotube.pro2.example

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

class AirAlerts(private val context: Context) {
    fun notify(pollutant: Pollutant, level: AirLevel, valueText: String) {
        if (!canPost()) return
        ensureChannel()
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle("${pollutant.label} is ${levelLabel(level)}")
            .setContentText("$valueText ${pollutant.unit}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(pollutant), notification)
    }

    fun cancel(pollutant: Pollutant) {
        NotificationManagerCompat.from(context).cancel(notificationId(pollutant))
    }

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Air quality alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when a selected pollutant reaches the level you chose."
        }
        manager.createNotificationChannel(channel)
    }

    private fun notificationId(pollutant: Pollutant): Int = NOTIFICATION_BASE + pollutant.ordinal

    companion object {
        private const val CHANNEL_ID = "air_alerts"
        private const val NOTIFICATION_BASE = 4100
        private const val REQUEST_OPEN = 7
    }
}
