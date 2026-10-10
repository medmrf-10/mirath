package com.irth.mirath.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder

/** خدمة أمامية: MediaSession حقيقية (شاشة قفل + سماعة + إعدادات سريعة) + إشعار وسائط حي. */
class PlayerService : Service() {
    private var session: MediaSession? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "toggle" -> { G.playPause(); return START_STICKY }
            "back" -> { G.seek((G.pos - 15).coerceAtLeast(0.0)); return START_STICKY }
            "fwd" -> { G.seek(G.pos + 15); return START_STICKY }
            "stop" -> { G.stopAll(); stopSelf(); return START_NOT_STICKY }
        }
        ensureSession()
        startForeground(11, buildNotif())
        pushState()
        return START_STICKY
    }

    override fun onDestroy() {
        session?.release(); session = null
        super.onDestroy()
    }

    private fun ensureSession() {
        if (session != null) return
        session = MediaSession(this, "mirath").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = G.play()
                override fun onPause() = G.pause()
                override fun onStop() { G.stopAll(); stopSelf() }
                override fun onSeekTo(pos: Long) = G.seek(pos / 1000.0)
                override fun onSkipToNext() { /* التالي داخل صفحة الدرس */ }
                override fun onSkipToPrevious() {}
            })
            isActive = true
        }
        Companion.session = session
    }

    private fun pushState() {
        val s = session ?: return
        val st = PlaybackState.Builder()
            .setState(
                if (G.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                (G.pos * 1000).toLong(), G.speed.toFloat(),
            )
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_STOP,
            ).build()
        s.setPlaybackState(st)
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, G.title.ifEmpty { "ميراث" })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "إرث")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, (G.dur * 1000).toLong())
                .build(),
        )
    }

    private fun buildNotif(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "تشغيل الدروس", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, PlayerService::class.java).setAction("toggle"),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stp = PendingIntent.getService(
            this, 2, Intent(this, PlayerService::class.java).setAction("stop"),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val back = PendingIntent.getService(
            this, 3, Intent(this, PlayerService::class.java).setAction("back"),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val fwd = PendingIntent.getService(
            this, 4, Intent(this, PlayerService::class.java).setAction("fwd"),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(G.title.ifEmpty { "ميراث" })
            .setContentText(if (G.playing) "قيد التشغيل" else "متوقف")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "‹15", back).build())
            .addAction(Notification.Action.Builder(null, if (G.playing) "إيقاف" else "تشغيل", toggle).build())
            .addAction(Notification.Action.Builder(null, "15›", fwd).build())
            .addAction(Notification.Action.Builder(null, "إنهاء", stp).build())
            .setOngoing(true)
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2).setMediaSession(session?.sessionToken))
            .build()
    }

    companion object {
        const val CH = "playback"
        var session: MediaSession? = null
            private set

        /** يُستدعى من نبض G كل ~3 ثوانٍ: يحدّث الإشعار وحالة الوسائط على شاشة القفل. */
        fun refresh(ctx: Context?) {
            ctx ?: return
            runCatching {
                if (G.videoId != null) {
                    ctx.startService(Intent(ctx, PlayerService::class.java))
                }
            }
        }
    }
}
