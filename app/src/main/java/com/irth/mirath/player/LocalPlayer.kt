package com.irth.mirath.player

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.irth.mirath.ui.theme.Pal
import kotlinx.coroutines.delay
import java.io.File

/**
 * مشغّل الوسائط المحلية: mp4 عبر VideoView وmp3 عبر MediaPlayer خفي —
 * نفس عقد YtPlayer (ctl + onTime + onEnded + onState) فيعمل مع المسرح الحي كما هو.
 */
@SuppressLint("InflateParams")
@Composable
fun LocalPlayer(
    file: File,
    ctl: YtCtl,
    startSec: Int = 0,
    onEnded: () -> Unit = {},
    onState: (Int) -> Unit = {},
    p: Pal,
    onTime: (Double) -> Unit,
) {
    val isVideo = file.extension.lowercase() in listOf("mp4", "webm", "mkv")
    val mp = remember { mutableStateOf<MediaPlayer?>(null) }
    val vv = remember { mutableStateOf<VideoView?>(null) }
    val endedFired = remember { booleanArrayOf(false) }

    DisposableEffect(file) {
        val player = MediaPlayer()
        var videoView: VideoView? = null
        ctl.seek = { sec -> runCatching { if (isVideo) videoView?.seekTo((sec * 1000).toInt()) else player.seekTo((sec * 1000).toInt()) } }
        ctl.play = { runCatching { if (isVideo) videoView?.start() else player.start() } }
        ctl.pause = { runCatching { if (isVideo) videoView?.pause() else player.pause() } }
        ctl.rate = { v -> runCatching { if (isVideo) videoView?.setOnPreparedListener { it.setPlaybackParams(it.playbackParams.setSpeed(v.toFloat())) } else player.playbackParams = player.playbackParams.setSpeed(v.toFloat()) } }
        ctl.isPlaying = { runCatching { if (isVideo) videoView?.isPlaying == true else player.isPlaying }.getOrDefault(false) }
        if (!isVideo) {
            runCatching {
                player.setDataSource(file.absolutePath)
                player.setOnPreparedListener {
                    if (startSec > 0) it.seekTo(startSec * 1000)
                    it.start()
                    onState(1)
                }
                player.setOnCompletionListener { onState(0) }
                player.prepareAsync()
            }
            mp.value = player
        }
        onDispose {
            ctl.seek = {}; ctl.play = {}; ctl.pause = {}; ctl.rate = {}; ctl.isPlaying = { false }
            runCatching { player.release() }
        }
    }

    // نبض الموضع — نفس استقصاء window.ct في YtPlayer
    LaunchedEffect(file) {
        while (true) {
            val t = runCatching {
                if (isVideo) (vv.value?.currentPosition ?: 0) / 1000.0
                else mp.value?.takeIf { it.isPlaying || it.currentPosition > 0 }?.currentPosition?.div(1000.0) ?: 0.0
            }.getOrDefault(0.0)
            onTime(t)
            val playing = ctl.isPlaying()
            val dur = runCatching {
                if (isVideo) vv.value?.duration ?: -1 else mp.value?.duration ?: -1
            }.getOrDefault(-1)
            if (!endedFired[0] && dur > 0 && t >= dur / 1000.0 - 0.4 && !playing) { endedFired[0] = true; onEnded() }
            delay(250)
        }
    }

    if (isVideo) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.fromFile(file))
                    setOnPreparedListener {
                        it.isLooping = false
                        if (startSec > 0) seekTo(startSec * 1000)
                        start()
                        onState(1)
                    }
                    setOnCompletionListener { onState(0) }
                    vv.value = this
                }
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(androidx.compose.ui.graphics.Color.Black),
        )
    } else {
        // مشغّل صوتي بصري بسيط — موجة نابضة باسم الملف
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(androidx.compose.foundation.shape.RoundedCornerShape(0.dp)).background(p.soft),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(p.gold.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text("♪", color = p.gold, fontSize = 30.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("صوت محلي — ${file.name}", color = p.muted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
