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
            "next" -> { G.skipNext(); return START_STICKY }
            "prev" -> { G.skipPrev(); return START_STICKY }
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
                override fun onSkipToNext() { G.skipNext() }
                override fun onSkipToPrevious() { G.skipPrev() }
                override fun onFastForward() = G.seek(G.pos + 15)
                override fun onRewind() = G.seek((G.pos - 15).coerceAtLeast(0.0))
                override fun onCustomAction(action: String, extras: android.os.Bundle?) {
                    if (action == "fwd") G.seek(G.pos + 15) else if (action == "back") G.seek((G.pos - 15).coerceAtLeast(0.0))
                }
            })
            isActive = true
        }
        Companion.session = session
    }

    private var artFor: String? = null
    private var art: android.graphics.Bitmap? = null

    /** صورة شاشة القفل: صورة الشيخ إن وُجدت، وإلا شعار ميراث — تُحمَّل مرة لكل علم. */
    private fun ensureArt() {
        val key = G.scienceId
        if (artFor == key && art != null) return
        artFor = key
        art = runCatching { android.graphics.BitmapFactory.decodeResource(resources, com.irth.mirath.R.mipmap.ic_launcher_foreground) }.getOrNull()
        val url = com.irth.mirath.data.Haw.photoOf(key) ?: return
        Thread {
            val bmp = runCatching {
                val req = coil.request.ImageRequest.Builder(this).data(url).allowHardware(false).size(512).build()
                val d = kotlinx.coroutines.runBlocking { coil.Coil.imageLoader(this@PlayerService).execute(req) }.drawable
                (d as? android.graphics.drawable.BitmapDrawable)?.bitmap
            }.getOrNull() ?: return@Thread
            if (artFor == key) { art = bmp; android.os.Handler(mainLooper).post { pushState(); runCatching { startForeground(11, buildNotif()) } } }
        }.start()
    }

    private fun pushState() {
        val s = session ?: return
        ensureArt()
        var acts = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_STOP or PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
        if (G.nextId != null) acts = acts or PlaybackState.ACTION_SKIP_TO_NEXT
        acts = acts or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        val st = PlaybackState.Builder()
            .setState(
                if (G.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                (G.pos * 1000).toLong(), if (G.playing) G.speed.toFloat() else 0f,
            )
            .setActions(acts)
            .addCustomAction(PlaybackState.CustomAction.Builder("back", "رجوع 15 ثانية", android.R.drawable.ic_media_rew).build())
            .addCustomAction(PlaybackState.CustomAction.Builder("fwd", "تقديم 15 ثانية", android.R.drawable.ic_media_ff).build())
            .build()
        s.setPlaybackState(st)
        val md = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, G.title.ifEmpty { "ميراث" })
            .putString(MediaMetadata.METADATA_KEY_ARTIST, G.artist.ifEmpty { "ميراث" })
            .putString(MediaMetadata.METADATA_KEY_ALBUM, G.seriesTitle.ifEmpty { "إرث" })
            .putLong(MediaMetadata.METADATA_KEY_DURATION, (G.dur * 1000).toLong())
        art?.let { md.putBitmap(MediaMetadata.METADATA_KEY_ART, it) }
        s.setMetadata(md.build())
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
        fun act(code: Int, a: String) = PendingIntent.getService(
            this, code, Intent(this, PlayerService::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE,
        )
        fun ic(r: Int) = android.graphics.drawable.Icon.createWithResource(this, r)
        val sub = listOf(G.artist, G.seriesTitle).filter { it.isNotEmpty() && it != "ميراث" }.joinToString(" · ")
        return Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(G.title.ifEmpty { "ميراث" })
            .setContentText(sub.ifEmpty { if (G.playing) "قيد التشغيل" else "متوقف" })
            .setSubText(G.sleepLabel())
            .setLargeIcon(art)
            .setContentIntent(open)
            .setDeleteIntent(stp)
            .addAction(Notification.Action.Builder(ic(android.R.drawable.ic_media_previous), "السابق", act(5, "prev")).build())
            .addAction(Notification.Action.Builder(ic(android.R.drawable.ic_media_rew), "‹15", act(3, "back")).build())
            .addAction(Notification.Action.Builder(ic(if (G.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play), if (G.playing) "إيقاف" else "تشغيل", toggle).build())
            .addAction(Notification.Action.Builder(ic(android.R.drawable.ic_media_ff), "15›", act(4, "fwd")).build())
            .addAction(Notification.Action.Builder(ic(android.R.drawable.ic_media_next), "التالي", act(6, "next")).build())
            .setOngoing(G.playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 2, 4).setMediaSession(session?.sessionToken))
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
