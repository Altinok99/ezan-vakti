package com.ezanvakti

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Notifier {
    const val CH_ALERT = "vakit_alert"
    const val CH_ONGOING = "vakit_ongoing"
    const val CH_PLAY = "ezan_play"
    private val fmt = DateTimeFormatter.ofPattern("HH:mm")

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Vakit bildirimleri", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ONGOING, "Sıradaki vakit (kalıcı)", NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_PLAY, "Ezan sesi", NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun openApp(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    /** Sessiz (yalnızca bildirim) uyarı. */
    fun alert(c: Context, idx: Int) {
        channels(c)
        val n = Notification.Builder(c, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${Prefs.NAMES[idx]} vakti girdi")
            .setContentText(Prefs.city(c) + " için ezan vakti")
            .setContentIntent(openApp(c))
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        try {
            c.getSystemService(NotificationManager::class.java).notify(100 + idx, n)
        } catch (_: SecurityException) {
        }
    }

    /** Kilit ekranında da görünen, geri sayımlı kalıcı bildirim. */
    fun persistent(c: Context, next: Next?) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (!Prefs.persistent(c) || next == null) {
            nm.cancel(1)
            return
        }
        channels(c)
        val times = Repo.times(c, next.at.toLocalDate()) ?: Repo.times(c, LocalDate.now(Repo.zone))
        val line = times?.let { t -> (0..5).joinToString("  ·  ") { "${Prefs.NAMES[it]} ${t[it].format(fmt)}" } } ?: ""
        val n = Notification.Builder(c, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Sıradaki: ${Prefs.NAMES[next.index]} ${next.at.format(fmt)}")
            .setContentText(line)
            .setStyle(Notification.BigTextStyle().bigText(line))
            .setWhen(next.at.toInstant().toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(c))
            .build()
        try {
            nm.notify(1, n)
        } catch (_: SecurityException) {
        }
    }
}

object Scheduler {
    /** Sıradaki vakit için alarm kurar, kalıcı bildirimi ve widget'ı günceller. */
    fun refresh(c: Context, nowMs: Long = System.currentTimeMillis()) {
        val now = Instant.ofEpochMilli(nowMs).atZone(Repo.zone)
        val next = Repo.nextPrayer(c, now)
        val am = c.getSystemService(AlarmManager::class.java)
        if (next != null) {
            val atMs = next.at.toInstant().toEpochMilli()
            val pi = PendingIntent.getBroadcast(
                c, 1,
                Intent(c, AlarmReceiver::class.java).putExtra("idx", next.index).putExtra("at", atMs),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        }
        Notifier.persistent(c, next)
        VakitWidget.updateAll(c)
    }

    fun refreshAsync(c: Context) {
        val app = c.applicationContext
        Thread { refresh(app) }.start()
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val idx = intent.getIntExtra("idx", -1)
        val at = intent.getLongExtra("at", 0L)
        val pending = goAsync()
        val app = c.applicationContext
        Thread {
            try {
                val fresh = System.currentTimeMillis() - at < 10 * 60 * 1000
                if (idx in 0..5 && fresh && Prefs.notify(app, idx)) {
                    if (Prefs.sound(app, idx)) {
                        app.startForegroundService(Intent(app, AdhanService::class.java).putExtra("idx", idx))
                    } else {
                        Notifier.alert(app, idx)
                    }
                }
                val nowMs = maxOf(System.currentTimeMillis(), at + 1000)
                Scheduler.refresh(app, nowMs)
                if (Repo.ensure(app)) Scheduler.refresh(app, nowMs)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val pending = goAsync()
        val app = c.applicationContext
        Thread {
            try {
                Scheduler.refresh(app)
                if (Repo.ensure(app)) Scheduler.refresh(app)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}
