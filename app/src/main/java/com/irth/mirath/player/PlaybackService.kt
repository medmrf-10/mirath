package com.irth.mirath.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.irth.mirath.R
import com.irth.mirath.data.Repo

/** خدمة وسائط أمامية: تُبقي الدرس حياً والشاشة مقفلة، وتعرض إشعار الوسائط وأزرار شاشة القفل. */
class PlaybackService : Service() {
    companion object {
        private const val CH = "playback"
        private const val NID = 7
        private var inst: PlaybackService? = null

        fun refresh(ctx: Context) {
            val s = inst
            if (s != null) s.post()
            else if (Playback.playing.value && Playback.cur.value != null) runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, PlaybackService::class.java))
            }
        }

        fun halt(ctx: Context) {
            inst?.let { it.stopForeground(Service.STOP_FOREGROUND_REMOVE); it.stopSelf() }
            NotificationManagerCompat.from(ctx).cancel(NID)
        }
    }

    private var fg = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        inst = this
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "تشغيل الدروس", NotificationManager.IMPORTANCE_LOW).apply {
                description = "التحكم في الدرس الجاري من الإشعار وشاشة القفل"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "toggle" -> Playback.toggle()
            "rew" -> Playback.seekBy(-Playback.SKIP)
            "ff" -> Playback.seekBy(Playback.SKIP)
            "close" -> { Playback.stop(); return START_NOT_STICKY }
        }
        post()
        return START_NOT_STICKY
    }

    private fun act(a: String, req: Int) = PendingIntent.getService(
        this, req, Intent(this, PlaybackService::class.java).setAction(a),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun post() {
        val id = Playback.cur.value
        if (id == null) { stopForeground(Service.STOP_FOREGROUND_REMOVE); stopSelf(); return }
        val v = Repo.video(id)
        val ser = Repo.seriesOfVideo(id)
        val playing = Playback.playing.value
        val open = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            putExtra("lesson", id)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val n = NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat_mirath)
            .setContentTitle(v?.title ?: "ميراث")
            .setContentText(ser?.title ?: "ميراث")
            .setLargeIcon(Playback.art)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(open?.let { PendingIntent.getActivity(this, 2, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) })
            .setDeleteIntent(act("close", 13))
            .addAction(android.R.drawable.ic_media_rew, "رجوع", act("rew", 10))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "إيقاف مؤقت" else "تشغيل", act("toggle", 11),
            )
            .addAction(android.R.drawable.ic_media_ff, "تقديم", act("ff", 12))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إغلاق", act("close", 13))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(Playback.session?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
        if (!fg) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                else startForeground(NID, n)
                fg = true
            }.onFailure { stopSelf() }
        } else runCatching { NotificationManagerCompat.from(this).notify(NID, n) }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!Playback.playing.value) Playback.stop()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        inst = null
        fg = false
        super.onDestroy()
    }
}
