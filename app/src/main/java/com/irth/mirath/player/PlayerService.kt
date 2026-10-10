package com.irth.mirath.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.irth.mirath.R

/** خدمة أمامية خفيفة: تبقي عملية التطبيق حية + إشعار وسائط بسيط (عنوان الدرس + إيقاف/تشغيل). */
class PlayerService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "toggle" -> { G.playPause(); return START_STICKY }
            "stop" -> { G.stopAll(); stopSelf(); return START_NOT_STICKY }
        }
        startForeground(11, buildNotif())
        return START_STICKY
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
        return Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(G.title.ifEmpty { "ميراث" })
            .setContentText(if (G.playing) "قيد التشغيل" else "متوقف")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, if (G.playing) "إيقاف" else "تشغيل", toggle).build())
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CH = "playback"
        fun refresh(ctx: Context?) {
            ctx ?: return
            runCatching {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (G.videoId != null && G.playing) {
                    // أعد بناء الإشعار ليعكس العنوان/الحالة الحالية كل ~3 ثوانٍ
                    ctx.startService(Intent(ctx, PlayerService::class.java))
                }
            }
        }
    }
}
