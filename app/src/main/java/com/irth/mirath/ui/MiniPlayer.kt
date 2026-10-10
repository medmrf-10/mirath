package com.irth.mirath.ui

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.irth.mirath.data.Repo
import com.irth.mirath.player.Playback
import com.irth.mirath.ui.theme.Pal

private fun clock(sec: Double): String {
    val s = sec.toInt().coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** مضيف WebView المشغّل العالمي — آخر مضيف يُركَّب يحمل الفيديو الحي. */
@Composable
fun WebHost(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> FrameLayout(ctx).also { Playback.claim(it) } },
        modifier = modifier,
        onRelease = { Playback.release(it) },
    )
}

@Composable
private fun VideoSurface(modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = Playback.setSurface(Surface(st))
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { Playback.setSurface(null); return true }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun AudioArt(p: Pal, label: String?) {
    Box(Modifier.fillMaxSize().background(p.soft), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(if (label != null) 64.dp else 30.dp).clip(CircleShape).background(p.gold.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Text("♪", color = p.gold, fontSize = if (label != null) 30.sp else 16.sp)
            }
            if (label != null) {
                Spacer(Modifier.height(10.dp))
                Text(label, color = p.muted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** شاشة المشغّل في صفحة الدرس: نفس إطار 16:9 — لكنها نافذة على المحرك العالمي لا مالكة له. */
@Composable
fun LessonPlayer(p: Pal, videoId: String, yt: String) {
    val ctx = LocalContext.current
    val isCur = Playback.cur.value == videoId
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !isCur -> {
                if (yt.isNotEmpty()) AsyncImage(
                    model = "https://i.ytimg.com/vi/$yt/hqdefault.jpg", contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                ) else AudioArt(p, null)
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(p.primary)
                        .clickable { Playback.open(ctx, videoId, autoplay = true) }
                        .semantics { contentDescription = "شغّل هذا الدرس" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PlayArrow, null, tint = p.card, modifier = Modifier.size(32.dp)) }
            }
            Playback.kind.value == "yt" -> WebHost(Modifier.fillMaxSize())
            Playback.kind.value == "video" -> VideoSurface(Modifier.fillMaxSize())
            else -> AudioArt(p, "صوت محلي")
        }
    }
}

/** المشغّل المصغّر — يتبع المستخدم فوق التبويبات، والنقر يعيده إلى موضعه في الدرس. */
@Composable
fun MiniPlayer(p: Pal, visible: Boolean, onOpen: (String) -> Unit) {
    val id = Playback.cur.value
    AnimatedVisibility(
        visible = visible && id != null,
        enter = slideInVertically(tween(240)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(160)),
    ) {
        val vid = id ?: return@AnimatedVisibility
        val v = Repo.video(vid) ?: return@AnimatedVisibility
        val ser = Repo.seriesOfVideo(vid)
        val playing = Playback.playing.value
        val pos = Playback.pos.value
        val dur = Playback.dur.value
        val f = if (dur > 0) (pos / dur).toFloat().coerceIn(0f, 1f) else 0f
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp)).background(p.card).border(1.dp, p.line, RoundedCornerShape(14.dp))
                .clickable(onClickLabel = "افتح الدرس") { onOpen(vid) },
        ) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(start = 8.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(86.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                    if (Playback.kind.value == "yt") WebHost(Modifier.fillMaxSize()) else AudioArt(p, null)
                    Box(Modifier.matchParentSize().clickable { onOpen(vid) })
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.title, color = p.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(ser?.title, "${clock(pos)} / ${clock(dur)}").joinToString(" · "),
                        color = p.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { Playback.seekBy(-Playback.SKIP) }) {
                    Icon(Icons.Filled.Replay10, "رجوع ١٠ ثوانٍ", tint = p.muted)
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(p.primary).clickable { Playback.toggle() }
                        .semantics { contentDescription = if (playing) "إيقاف مؤقت" else "تشغيل" },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = p.card, modifier = Modifier.size(26.dp)) }
                IconButton(onClick = { Playback.stop() }) { Icon(Icons.Filled.Close, "إغلاق المشغّل", tint = p.muted) }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(p.soft)) {
                Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(p.gold))
            }
        }
    }
}
