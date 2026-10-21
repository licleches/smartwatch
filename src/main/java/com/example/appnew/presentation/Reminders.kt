package com.example.appnew.presentation

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.ZoneId

object Reminders {
    const val CHANNEL = "meds_alarm_v3"
    const val ACTION_TAKEN = "com.example.appnew.TAKEN"
    const val ACTION_SNOOZE = "com.example.appnew.SNOOZE"
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /** Canal silencioso: el sonido y la vibración los maneja AlarmService. */
    fun createChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("meds")
        nm.deleteNotificationChannel("meds_alarm")
        val ch = NotificationChannel(CHANNEL, "Alarma de medicamentos", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun alarmIntent(c: Context, id: Int, snooze: Boolean = false) = PendingIntent.getBroadcast(
        c, if (snooze) 200000 + id else id,
        Intent(c, ReminderReceiver::class.java).putExtra("id", id).putExtra("snooze", snooze), FLAGS
    )

    /** Alarma de reloj real (setAlarmClock): exacta, funciona con Doze y no pide permisos extra. */
    private fun setAlarm(c: Context, millis: Long, pi: PendingIntent) {
        val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val am = c.getSystemService(AlarmManager::class.java)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), pi)
        } catch (e: SecurityException) {   // plan B si falta el permiso: nunca cerrar la app
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    /** Programa la próxima alarma (hoy si aún no pasa la hora, si no mañana). */
    fun schedule(c: Context, med: Medication) {
        val now = LocalDateTime.now()
        var next = now.withHour(med.hour).withMinute(med.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        setAlarm(c, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), alarmIntent(c, med.id))
    }

    /** Vuelve a sonar en [minutes] minutos sin tocar la alarma diaria. */
    fun snooze(c: Context, id: Int, minutes: Long = 10) =
        setAlarm(c, System.currentTimeMillis() + minutes * 60_000, alarmIntent(c, id, true))

    fun cancel(c: Context, med: Medication) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(alarmIntent(c, med.id)); am.cancel(alarmIntent(c, med.id, true))
    }

    /** Detiene el sonido y la vibración y quita la notificación. */
    fun stopAlarm(c: Context, id: Int) {
        c.stopService(Intent(c, AlarmService::class.java))
        c.getSystemService(NotificationManager::class.java).cancel(id)
    }

    private fun action(c: Context, act: String, id: Int) = PendingIntent.getBroadcast(
        c, (if (act == ACTION_TAKEN) 100000 else 300000) + id,
        Intent(c, ReminderReceiver::class.java).setAction(act).putExtra("id", id), FLAGS
    )

    /** Notificación de alarma con pantalla completa y botones Tomé / Posponer. */
    fun buildNotification(c: Context, id: Int, name: String, time: String): Notification {
        createChannel(c)
        val full = PendingIntent.getActivity(
            c, id,
            Intent(c, AlarmActivity::class.java).putExtra("id", id).putExtra("name", name)
                .putExtra("time", time).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), FLAGS
        )
        return NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Hora de tu medicamento")
            .setContentText("$name - $time")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Tomé", action(c, ACTION_TAKEN, id))
            .addAction(0, "Posponer 10 min", action(c, ACTION_SNOOZE, id))
            .build()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        when (i.action) {
            Reminders.ACTION_TAKEN -> { MedStore.markTaken(c, id); Reminders.stopAlarm(c, id) }
            Reminders.ACTION_SNOOZE -> { Reminders.stopAlarm(c, id); Reminders.snooze(c, id) }
            else -> {                                    // llegó la hora de la alarma
                val med = MedStore.load(c).find { it.id == id } ?: return
                try {
                    ContextCompat.startForegroundService(c, Intent(c, AlarmService::class.java).putExtra("id", id))
                } catch (e: Exception) {                 // plan B: solo notificación
                    c.getSystemService(NotificationManager::class.java)
                        .notify(id, Reminders.buildNotification(c, id, med.name, med.timeText))
                }
                if (!i.getBooleanExtra("snooze", false)) Reminders.schedule(c, med) // mañana
            }
        }
    }
}