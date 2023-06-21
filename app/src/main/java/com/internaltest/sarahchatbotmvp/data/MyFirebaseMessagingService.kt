package com.internaltest.sarahchatbotmvp.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.internaltest.sarahchatbotmvp.utils.createNotificationChannel

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val firestoreRepo = FirestoreRepo()

    @SuppressLint("MissingPermission")
    private fun sendNotification(title: String, message: String) {
        createNotificationChannel(this)

        val channelId = Constants.FIREBASE_CHANNEL_ID
        val notificationId = 1 // Can be any unique integer

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, notificationBuilder.build())
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            /*
            If the title and message are provided in the console, they will be used to display the notification.
            If not, the default values "Default Title" and "Default Message" will be used as fallbacks.
            * */
            sendNotification(it.title ?: "Default Title", it.body ?: "Default Message")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM Token", "New FCM Token: $token")
        CoroutineScope(Dispatchers.IO).launch {
            firestoreRepo.saveFcmToken(token)
        }
    }
}
