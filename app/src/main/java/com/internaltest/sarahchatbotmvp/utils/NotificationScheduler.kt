package com.internaltest.sarahchatbotmvp.utils

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    const val DAILY_REQUEST_CODE = 101
    const val INACTIVE_REQUEST_CODE = 102
    private const val INACTIVE_WORK_TAG = "InactiveNotificationWork"
    const val XMAS_REQUEST_CODE = 103

    fun checkAndRequestScheduleExactAlarmPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val alarmManager = ContextCompat.getSystemService(activity, AlarmManager::class.java)
            if (alarmManager?.canScheduleExactAlarms() == false) {
                Toast.makeText(activity, "Precisamos de sua permissão" +
                        "para você receber notificações!", Toast.LENGTH_LONG).show()

                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                activity.startActivity(intent)
            }
        }
    }

    fun scheduleDailyNotification(context: Context) {
        scheduleNotification(context, DAILY_REQUEST_CODE, 1, TimeUnit.DAYS)
    }

    fun scheduleInactiveNotification(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val workRequest = PeriodicWorkRequestBuilder<InactiveNotificationWorker>(
            6, TimeUnit.HOURS) // You can set your own time interval for the check.
            .addTag(INACTIVE_WORK_TAG)
            .build()

        // Enqueue the work request, keeping any previously scheduled work requests
        workManager.enqueueUniquePeriodicWork(
            INACTIVE_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest)
    }

    fun scheduleXmasNotification(context: Context) {
        val xmasCal = getUpcomingXmasCalendar()

        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmNotificationReceiver::class.java).apply {
            putExtra("notification_code", XMAS_REQUEST_CODE)
        }
        val pendingIntent = PendingIntent.getBroadcast(context, XMAS_REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmMgr.canScheduleExactAlarms()) {
                alarmMgr.setExact(AlarmManager.RTC_WAKEUP, xmasCal.timeInMillis, pendingIntent)
            } else {
                alarmMgr.setAlarmClock(
                    AlarmManager.AlarmClockInfo(xmasCal.timeInMillis, pendingIntent),
                    pendingIntent
                )
            }
        } else {
            alarmMgr.setExact(AlarmManager.RTC_WAKEUP, xmasCal.timeInMillis, pendingIntent)
        }
    }

    private fun scheduleNotification(context: Context, requestCode: Int, delay: Long, timeUnit: TimeUnit) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notificationIntent = Intent(context, AlarmNotificationReceiver::class.java).apply {
            putExtra("notification_code", requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Cancel the existing alarm (if any) before scheduling a new one
        alarmManager.cancel(pendingIntent)

        val calendar = Calendar.getInstance().apply {
            add(getCalendarFieldForTimeUnit(timeUnit), delay.toInt())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent),
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }

    private fun getCalendarFieldForTimeUnit(timeUnit: TimeUnit): Int {
        return when (timeUnit) {
            TimeUnit.DAYS -> Calendar.DAY_OF_YEAR
            TimeUnit.HOURS -> Calendar.HOUR_OF_DAY
            TimeUnit.MINUTES -> Calendar.MINUTE
            else -> throw IllegalArgumentException("Unsupported time unit")
        }
    }

    private fun getUpcomingXmasCalendar(): Calendar {
        val nowCal = Calendar.getInstance()
        val xmasCal = Calendar.getInstance().apply {
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 25)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        if (xmasCal.before(nowCal))
            xmasCal.add(Calendar.YEAR, 1)
        return xmasCal
    }
}
