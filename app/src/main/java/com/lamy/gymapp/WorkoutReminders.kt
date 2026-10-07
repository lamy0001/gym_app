package com.lamy.gymapp

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lamy.gymapp.data.GymDatabase
import com.lamy.gymapp.data.nextReminder
import com.lamy.gymapp.data.reminderTime
import com.lamy.gymapp.data.trainingDays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

private const val ReminderId = 4107

internal fun scheduleReminder(context: Context, enabled: Boolean, time: String, days: Set<Int>) {
    val pending = PendingIntent.getBroadcast(context, ReminderId, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val alarm = context.getSystemService(AlarmManager::class.java)
    alarm.cancel(pending)
    if (!enabled) {
        context.getSystemService(NotificationManager::class.java).cancel(ReminderId)
        return
    }
    val next = nextReminder(ZonedDateTime.now(), reminderTime(time), days) ?: return
    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
}

internal fun showWorkoutReminder(context: Context) {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel("workout_reminders", "Lembretes de treino", NotificationManager.IMPORTANCE_DEFAULT))
    val openApp = PendingIntent.getActivity(context, ReminderId,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(context, "workout_reminders")
        .setSmallIcon(com.lamy.gymapp.R.drawable.ic_launcher)
        .setContentTitle("Hora do treino")
        .setContentText("Toque para abrir seu treino de hoje.")
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .build()
    manager.notify(ReminderId, notification)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = refreshReminders(context, deliver = true, finish = goAsync())
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = refreshReminders(context, deliver = false, finish = goAsync())
}

private fun refreshReminders(context: Context, deliver: Boolean, finish: BroadcastReceiver.PendingResult) {
    CoroutineScope(Dispatchers.IO).launch {
        val database = GymDatabase.create(context)
        try {
            val settings = database.dao().allSettings().associate { it.key to it.value }
            val enabled = settings["remindersEnabled"] != "false"
            val days = trainingDays(settings["trainingDays"])
            scheduleReminder(context, enabled, settings["reminderTime"] ?: "07:00", days)
            if (deliver && enabled && ZonedDateTime.now().dayOfWeek.value in days) showWorkoutReminder(context)
        } finally {
            database.close()
            finish.finish()
        }
    }
}
