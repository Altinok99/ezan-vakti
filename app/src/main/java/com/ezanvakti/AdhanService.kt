package com.ezanvakti

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.io.File

/** Ezan sesini çalan ön plan servisi. Ses: kullanıcının seçtiği dosya > res/raw/ezan > sistem bildirim sesi. */
class AdhanService : Service() {
    private var mp: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        val idx = (intent?.getIntExtra("idx", 0) ?: 0).coerceIn(0, 5)
        Notifier.channels(this)

        val stop = PendingIntent.getService(
            this, 2, Intent(this, AdhanService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, Notifier.CH_PLAY)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${Prefs.NAMES[idx]} vakti girdi")
            .setContentText(Prefs.city(this) + " için ezan vakti")
            .setContentIntent(Notifier.openApp(this))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat), "Durdur", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(2, n)
        }
        play()
        handler.postDelayed({ stopSelf() }, 6 * 60 * 1000L)
        return START_NOT_STICKY
    }

    private fun soundUri(): Uri {
        val f = File(filesDir, "ezan.mp3")
        if (f.exists() && f.length() > 0) return Uri.fromFile(f)
        val rawId = resources.getIdentifier("ezan", "raw", packageName)
        if (rawId != 0) return Uri.parse("android.resource://$packageName/$rawId")
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun play() {
        try {
            mp?.release()
            mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@AdhanService, soundUri())
                setOnCompletionListener { stopSelf() }
                setOnErrorListener { _, _, _ -> stopSelf(); true }
                prepare()
                start()
            }
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { mp?.stop() } catch (_: Exception) {}
        mp?.release()
        mp = null
        super.onDestroy()
    }
}
