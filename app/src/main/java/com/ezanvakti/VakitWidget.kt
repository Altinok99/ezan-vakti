package com.ezanvakti

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class VakitWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, build(context))
    }

    companion object {
        private val fmt = DateTimeFormatter.ofPattern("HH:mm")

        fun updateAll(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            val ids = m.getAppWidgetIds(ComponentName(c, VakitWidget::class.java))
            if (ids.isNotEmpty()) m.updateAppWidget(ids, build(c))
        }

        fun build(c: Context): RemoteViews {
            val rv = RemoteViews(c.packageName, R.layout.widget)
            rv.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
            val now = ZonedDateTime.now(Repo.zone)
            val next = Repo.nextPrayer(c, now)
            val times = next?.let { Repo.times(c, it.at.toLocalDate()) }
                ?: Repo.times(c, LocalDate.now(Repo.zone))

            val ids = listOf(
                R.id.w_n0 to R.id.w_t0, R.id.w_n1 to R.id.w_t1, R.id.w_n2 to R.id.w_t2,
                R.id.w_n3 to R.id.w_t3, R.id.w_n4 to R.id.w_t4, R.id.w_n5 to R.id.w_t5
            )
            val gold = Color.parseColor("#FFC857")
            for (i in 0..5) {
                val (nId, tId) = ids[i]
                rv.setTextViewText(nId, Prefs.NAMES[i])
                rv.setTextViewText(tId, times?.get(i)?.format(fmt) ?: "--:--")
                val hot = next != null && next.index == i
                rv.setTextColor(tId, if (hot) gold else Color.WHITE)
                rv.setTextColor(nId, if (hot) gold else Color.parseColor("#9FB8C6"))
            }

            if (next == null) {
                rv.setTextViewText(R.id.w_title, "Veri yok - uygulamayı açın")
                rv.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime(), null, false)
            } else {
                rv.setTextViewText(R.id.w_title, "Sıradaki: ${Prefs.NAMES[next.index]} · ${Prefs.city(c)}")
                val remain = next.at.toInstant().toEpochMilli() - System.currentTimeMillis()
                rv.setChronometerCountDown(R.id.w_chrono, true)
                rv.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime() + remain, null, true)
            }
            return rv
        }
    }
}
