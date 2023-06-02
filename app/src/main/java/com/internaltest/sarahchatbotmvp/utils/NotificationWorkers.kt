package com.internaltest.sarahchatbotmvp.utils

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.firestore.FieldValue
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
import com.internaltest.sarahchatbotmvp.ui.main.MainActivity
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

private const val DAILY_NOTIFICATION_ID = 1
private const val INACTIVE_NOTIFICATION_ID = 2
private const val XMAS_NOTIFICATION_ID = 3
private const val CHANNEL_ID = "app_notifications"

private fun createNotificationChannel(context: Context) {
    val name = "App Notifications"
    val descriptionText = "Notifications related to the app"
    val importance = NotificationManager.IMPORTANCE_DEFAULT
    val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
        description = descriptionText
    }

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)
}

@SuppressLint("MissingPermission")
fun showDailyNotification(context: Context) {
    createNotificationChannel(context)

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher) // Replace with your own drawable
        .setContentTitle("\uD83E\uDD29 Olá! É a Samay aqui")
        .setContentText("Vamos conversar um pouco!")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    with(NotificationManagerCompat.from(context)) {
        notify(DAILY_NOTIFICATION_ID, notificationBuilder.build())
    }
}

@SuppressLint("MissingPermission")
suspend fun showInactiveNotificationIfNeeded(context: Context) {
    val firestoreRepo = FirestoreRepo()

    // Get the user's last_active and last_notification_check timestamps
    val userDocument = firestoreRepo.getUserDocument()
    val userSnapshot = userDocument?.get()?.await()
    val lastActive = userSnapshot?.getTimestamp("last_active")?.toDate()?.time
    val lastNotificationCheck = userSnapshot?.getTimestamp("last_notification_check")?.toDate()?.time

    // When the app was inactive for the specified duration
    if (lastActive != null && lastNotificationCheck != null &&
        lastActive <= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)) {

        // Send the inactive notification
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // Replace with your own drawable
            .setContentTitle("\uD83D\uDE14 Olá...")
            .setContentText("Já faz um tempo que você não usa o app!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(INACTIVE_NOTIFICATION_ID, notificationBuilder.build())
        }

        // Update the last_notification_check timestamp
        userDocument.update("last_notification_check", FieldValue.serverTimestamp()).await()
    }
}

@SuppressLint("MissingPermission")
fun showXmasNotification(context: Context) {
    createNotificationChannel(context)

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher) // Replace with your own drawable
        .setContentTitle("\uD83C\uDF84 \uD83D\uDD4E Boas Festas!")
        .setContentText("Desejamos boas festividades e um ótimo dia!")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    with(NotificationManagerCompat.from(context)) {
        notify(XMAS_NOTIFICATION_ID, notificationBuilder.build())
    }
}