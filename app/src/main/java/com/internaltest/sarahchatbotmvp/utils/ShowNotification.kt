package com.internaltest.sarahchatbotmvp.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.internaltest.sarahchatbotmvp.R

const val CHANNEL_ID = "1"
const val CHANNEL_NAME = "NotifChannel"

class ShowNotification(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        createNotificationChannel()
        showDailyNotification()
        return Result.success()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun showDailyNotification() {
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Olá! É a Samay aqui!")
            .setContentText("Vamos conversar um pouco?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, builder.build())
    }
}
