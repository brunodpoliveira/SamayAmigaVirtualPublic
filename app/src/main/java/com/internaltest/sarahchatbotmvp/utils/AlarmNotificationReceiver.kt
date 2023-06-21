package com.internaltest.sarahchatbotmvp.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationCode = intent.getIntExtra("notification_code", -1)

        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            // Reschedule notifications on device boot
            NotificationScheduler.scheduleDailyNotification(context)
            NotificationScheduler.scheduleInactiveNotification(context)
            NotificationScheduler.scheduleXmasNotification(context)
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                when (notificationCode) {
                    NotificationScheduler.DAILY_REQUEST_CODE -> {
                        showDailyNotification(context)
                        // Reschedule the daily notification after it's shown
                        NotificationScheduler.scheduleDailyNotification(context)
                    }
                    NotificationScheduler.INACTIVE_REQUEST_CODE -> showInactiveNotificationIfNeeded(context)
                    NotificationScheduler.XMAS_REQUEST_CODE -> showXmasNotification(context)
                }
            }
        }
    }
}
