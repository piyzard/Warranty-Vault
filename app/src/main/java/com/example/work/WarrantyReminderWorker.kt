package com.example.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.data.AppDatabase

class WarrantyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val receiptId = inputData.getLong("receipt_id", -1L)
        if (receiptId == -1L) return Result.failure()

        // 1. Check dynamic user configuration
        val sharedPrefs = applicationContext.getSharedPreferences("vault_auth_prefs", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPrefs.getBoolean("notifications_enabled", true)
        if (!notificationsEnabled) {
            android.util.Log.d("WarrantyReminderWorker", "Notifications globally disabled by user. Skipping notification.")
            return Result.success()
        }

        val thresholdDays = inputData.getInt("threshold_days", 30)
        val isThresholdEnabled = when (thresholdDays) {
            30 -> sharedPrefs.getBoolean("remind_30_days", true)
            7 -> sharedPrefs.getBoolean("remind_7_days", true)
            1 -> sharedPrefs.getBoolean("remind_1_day", true)
            else -> true
        }

        if (!isThresholdEnabled) {
            android.util.Log.d("WarrantyReminderWorker", "Notification for $thresholdDays-day threshold disabled. Skipping.")
            return Result.success()
        }

        // 2. Fetch receipt from DB to make sure it's still there and has warranty
        val db = AppDatabase.getDatabase(applicationContext)
        val receipt = db.receiptDao().getReceiptById(receiptId) ?: return Result.success()

        if (!receipt.hasWarranty) return Result.success()

        val daysRemaining = receipt.getDaysRemaining()
        
        // Define clean custom notification message depending on threshold
        val title = "Warranty Reminder: $thresholdDays Days Left!"
        val content = "${receipt.merchantName} protection expires in $daysRemaining days on ${receipt.warrantyExpiryDate}."

        showNotification(receiptId.toInt() * 100 + thresholdDays, title, content)

        return Result.success()
    }

    private fun showNotification(id: Int, title: String, content: String) {
        val channelId = "warranty_reminders"
        val channelName = "Warranty Reminders"
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when warranties are close to expiring."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Using standard system icon android.R.drawable.ic_dialog_info since we don't have custom drawables yet,
        // which ensures compile and runtime safety.
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }
}
