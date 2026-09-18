package com.example.smartwindow.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.smartwindow.MainActivity
import com.example.smartwindow.R

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Smart Window Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for rain detection, Wi-Fi disconnect, and security"
                enableVibration(true)
                enableLights(true)
            }

            val statusChannel = NotificationChannel(
                CHANNEL_STATUS_ID,
                "Window Status Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Updates on window opening, closing, and temperature"
            }

            notificationManager.createNotificationChannel(alertsChannel)
            notificationManager.createNotificationChannel(statusChannel)
        }
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    fun sendRainAlert() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("🌧️ Rain Detected!")
            .setContentText("Moisture detected on rain sensor! Windows are closing automatically.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        notificationManager.notify(NOTIF_ID_RAIN, notification)
    }

    fun sendDisconnectAlert() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("⚠️ Smart Window Disconnected!")
            .setContentText("ESP32 lost connection! Safety lock activated: Windows secured and system paused.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .build()

        notificationManager.notify(NOTIF_ID_DISCONNECT, notification)
    }

    fun sendWindowMovement(isOpen: Boolean) {
        val title = if (isOpen) "🔓 Window Opened" else "🔒 Window Closed"
        val text = if (isOpen) "Smart Window is now open at 180° for ventilation." else "Smart Window is now closed at 0°."
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())
            .build()

        notificationManager.notify(NOTIF_ID_WINDOW, notification)
    }

    fun sendTempAlert(diff: Float) {
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("🌡️ Temperature Difference Alert")
            .setContentText("Room temperature difference is ${String.format("%.1f", diff)}°C. Automatic ventilation engaged.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())
            .build()

        notificationManager.notify(NOTIF_ID_TEMP, notification)
    }

    companion object {
        const val CHANNEL_ALERTS_ID = "smart_window_alerts"
        const val CHANNEL_STATUS_ID = "smart_window_status"

        const val NOTIF_ID_RAIN = 1001
        const val NOTIF_ID_DISCONNECT = 1002
        const val NOTIF_ID_WINDOW = 1003
        const val NOTIF_ID_TEMP = 1004
    }
}
