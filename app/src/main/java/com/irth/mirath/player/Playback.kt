package com.irth.mirath.player

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.irth.mirath.data.LocalLib
import com.irth.mirath.data.Repo
import com.irth.mirath.store.Prefs
import okhttp3.OkHttpClient
import okhttp3.Request

/** WebView لا يعلن اختفاءه — فيستمر صوت يوتيوب عند قفل الشاشة أو الخروج من التطبيق. */
class BgWebView(ctx: Context) : WebView(ctx) {
    override fun onWindowVisibilityChanged(visibility: Int) = super.onWindowVisibilityChanged(View.VISIBLE)
}

/**
 * محرك التشغيل العالمي: يعيش خارج الشاشات، فيتبع المستخدمَ مشغّلاً مصغّراً، ويستمر خلف قفل الشاشة
 * عبر MediaSession + خدمة وسائط أمامية. يدير يوتيوب (WebView واحد يُنقل بين المضيفات) والمحلي (MediaPlayer).
 */
@SuppressLint("StaticFieldLeak")
object Playback {
    const val SKIP = 10.0

    val cur = mutableStateOf<String?>(null)
    val kind = mutableStateOf("yt")
    val pos = mutableStateOf(0.0)
    val dur = mutableStateOf(0.0)
    val playing = mutableStateOf(false)
    val speed = mutableStateOf(1.0)
    val sleepMin = mutableStateOf(0)
    val advanced = mutableStateOf<Pair<String, String>?>(null)
    val openRequest = mutableStateOf<String?>(null)

    private var app: Context? = null
    private var wv: BgWebView? = null
    private var wvCtx: MutableContextWrapper? = null
    private val hosts = mutableListOf<FrameLayout>()
    private var mp: MediaPlayer? = null
    private var mpReady = false
    private var surface: Surface? = null
    private var key = ""
    private var endedFired = false
    private var sleepAt = 0L
    private var lastSave = 0L
    private var lastListen = 0L
    private var lastStatePush = 0L
    var session: MediaSessionCompat? = null; private set
    var art: Bitmap? = null; private set
    private var artFor = ""
    private val main = Handler(Looper.getMainLooper())
    private val http by lazy { OkHttpClient() }
    private var focusReq: AudioFocusRequestCompat? = null
    private var resumeOnGain = false
    private var noisyOn = false
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    fun init(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        session = MediaSessionCompat(ctx.applicationContext, "mirath").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = play()
                override fun onPause() = pause()
                override fun onStop() = stop()
                override fun onSeekTo(pos: Long) = seek(pos / 1000.0)
                override fun onFastForward() = seekBy(SKIP)
                override fun onRewind() = seekBy(-SKIP)
                override fun onSkipToNext() = skip(1)
                override fun onSkipToPrevious() = skip(-1)
                override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                    when (action) { "rew" -> seekBy(-SKIP); "ff" -> seekBy(SKIP) }
                }
            })
            val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            if (launch != null) setSessionActivity(
                android.app.PendingIntent.getActivity(ctx, 1, launch, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        main.post(tick)
    }

    // ───── مضيفات WebView: آخر مضيف مُطالِب يحمل المشغّل (صفحة الدرس ← المصغّر ← الموقف الخفي) ─────
    fun claim(fl: FrameLayout) { hosts.remove(fl); hosts.add(fl); reattach() }
    fun release(fl: FrameLayout) { hosts.remove(fl); reattach() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun web(ctx: Context): BgWebView {
        wv?.let { wvCtx?.setBaseContext(ctx); return it }
        val wrap = MutableContextWrapper(ctx).also { wvCtx = it }
        return BgWebView(wrap).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            setBackgroundColor(android.graphics.Color.BLACK)
            wv = this
        }
    }

    private fun reattach() {
        val top = hosts.lastOrNull() ?: return
        val w = web(top.context)
        if (w.parent === top) return
        (w.parent as? ViewGroup)?.removeView(w)
        top.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun setSurface(s: Surface?) { surface = s; runCatching { mp?.setSurface(s) } }

    // ───── فتح درس ─────
    fun isCur(id: String) = cur.value == id

    fun open(ctx: Context, id: String, autoplay: Boolean = false) {
        init(ctx)
        val v = Repo.video(id) ?: return
        val ser = Repo.seriesOfVideo(id)
        val media = LocalLib.mediaFor(id)?.takeIf { v.yt.isEmpty() || Prefs.src(ser?.id ?: "") == "local" }
        val k = "$id|${media?.path ?: v.yt}"
        if (k == key) { if (autoplay && !playing.value) play(); return }
        saveNow()
        teardownEngine()
        key = k
        cur.value = id
        endedFired = false
        val start = if (Prefs.finished(id, v.duration)) 0 else Prefs.progress(id)
        pos.value = start.toDouble()
        dur.value = v.duration.toDouble()
        playing.value = false
        if (media != null) {
            kind.value = if (media.extension.lowercase() in listOf("mp4", "webm", "mkv")) "video" else "audio"
            val player = MediaPlayer()
            mp = player; mpReady = false
            runCatching {
                player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                player.setWakeMode(ctx.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                player.setDataSource(media.absolutePath)
                surface?.let { player.setSurface(it) }
                player.setOnPreparedListener {
                    mpReady = true
                    if (it.duration > 0) dur.value = it.duration / 1000.0
                    if (start > 0) it.seekTo(start * 1000)
                    play()
                }
                player.setOnCompletionListener { ended() }
                player.prepareAsync()
            }
        } else {
            kind.value = "yt"
            val w = web(hosts.lastOrNull()?.context ?: ctx)
            w.loadDataWithBaseURL("https://www.youtube-nocookie.com", ytHtml(v.yt, start, autoplay, speed.value), "text/html", "utf-8", null)
            if (autoplay) gainFocus()
        }
        pushMeta()
        loadArt(v.yt)
    }

    private fun ytHtml(vid: String, start: Int, auto: Boolean, rate: Double) = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;background:#000;height:100%}#p{position:fixed;inset:0;width:100%;height:100%}</style></head>
<body><div id="p"></div><script>
var pl;window.ct=$start;window.ps=-1;
function onYouTubeIframeAPIReady(){pl=new YT.Player('p',{videoId:'$vid',
playerVars:{rel:0,playsinline:1,enablejsapi:1,start:$start${if (auto) ",autoplay:1" else ""}},
events:{onReady:function(){try{pl.setPlaybackRate($rate);${if (auto) "pl.playVideo();" else ""}}catch(e){}},
onStateChange:function(e){window.ps=e.data;if(e.data==1)window.ct=pl.getCurrentTime()}}});}
function st(){try{if(pl&&pl.getCurrentTime)window.ct=pl.getCurrentTime();return window.ct+','+window.ps+','+((pl&&pl.getDuration)?pl.getDuration():0)}catch(e){return ''}}
</script><script src="https://www.youtube.com/iframe_api"></script></body></html>"""

    private fun js(s: String) { wv?.evaluateJavascript("try{$s}catch(e){}", null) }

    private fun teardownEngine() {
        runCatching { mp?.release() }
        mp = null; mpReady = false
        if (kind.value == "yt") js("pl&&pl.stopVideo()")
    }

    // ───── التحكم ─────
    fun play() {
        if (cur.value == null) return
        gainFocus()
        when (kind.value) {
            "yt" -> js("pl&&pl.playVideo()")
            else -> mp?.takeIf { mpReady }?.let {
                runCatching { it.start(); if (speed.value != 1.0) it.playbackParams = it.playbackParams.setSpeed(speed.value.toFloat()) }
                setPlaying(true)
            }
        }
    }

    fun pause() {
        when (kind.value) {
            "yt" -> js("pl&&pl.pauseVideo()")
            else -> mp?.takeIf { mpReady }?.let { runCatching { it.pause() }; setPlaying(false) }
        }
        saveNow()
    }

    fun toggle() { if (playing.value) pause() else play() }

    fun seek(sec: Double) {
        val s = sec.coerceAtLeast(0.0)
        when (kind.value) {
            "yt" -> js("pl&&(pl.seekTo($s,true),pl.playVideo())")
            else -> mp?.takeIf { mpReady }?.let { runCatching { it.seekTo((s * 1000).toInt()) } }
        }
        pos.value = s
        pushState(true)
    }

    fun seekBy(d: Double) {
        val max = if (dur.value > 0) dur.value - 1 else Double.MAX_VALUE
        seek((pos.value + d).coerceIn(0.0, max))
    }

    fun setSpeed(v: Double) {
        speed.value = v
        when (kind.value) {
            "yt" -> js("pl&&pl.setPlaybackRate($v)")
            else -> mp?.takeIf { mpReady && it.isPlaying }?.let { runCatching { it.playbackParams = it.playbackParams.setSpeed(v.toFloat()) } }
        }
        pushState(true)
    }

    /** مؤقت النوم بالدقائق — 0 يلغيه. يعمل والشاشة مقفلة لأنه في المحرك لا في الصفحة. */
    fun setSleep(min: Int) {
        sleepMin.value = min
        sleepAt = if (min > 0) System.currentTimeMillis() + min * 60_000L else 0L
    }

    fun skip(dir: Int) {
        val id = cur.value ?: return
        val ser = Repo.seriesOfVideo(id) ?: return
        val i = ser.items.indexOf(id) + dir
        val ctx = app ?: return
        if (i in ser.items.indices) { val n = ser.items[i]; advanced.value = id to n; open(ctx, n, autoplay = true) }
        else if (dir < 0) seek(0.0)
    }

    fun stop() {
        saveNow()
        teardownEngine()
        if (kind.value == "yt") wv?.loadUrl("about:blank")
        key = ""; cur.value = null; setPlaying(false)
        setSleep(0)
        loseFocus()
        session?.isActive = false
        app?.let { PlaybackService.halt(it) }
    }

    // ───── نهاية الدرس: حفظه مُتمّاً ثم التالي غير المُتمّ في السلسلة — ولو والشاشة مقفلة ─────
    private fun ended() {
        if (endedFired) return
        endedFired = true
        val id = cur.value ?: return
        val v = Repo.video(id)
        val full = maxOf(dur.value.toInt(), v?.duration ?: 0)
        if (full > 0) Prefs.setProgress(id, full)
        setPlaying(false)
        val ser = Repo.seriesOfVideo(id)
        val next = ser?.let { s ->
            val idx = s.items.indexOf(id)
            s.items.drop(idx + 1).firstOrNull { nid -> Repo.video(nid)?.let { !Prefs.finished(nid, it.duration) } == true }
        }
        val ctx = app
        if (next != null && ctx != null) { advanced.value = id to next; open(ctx, next, autoplay = true) }
    }

    private fun saveNow() {
        val id = cur.value ?: return
        if (pos.value > 0 && !endedFired) Prefs.setProgress(id, pos.value.toInt())
    }

    // ───── نبض واحد لكل المحركات ─────
    private val tick = object : Runnable {
        override fun run() {
            runCatching { pulse() }
            main.postDelayed(this, 300)
        }
    }

    private fun pulse() {
        if (cur.value == null) return
        val now = System.currentTimeMillis()
        if (kind.value == "yt") {
            wv?.evaluateJavascript("typeof st=='function'?st():''") { r ->
                val parts = r?.removeSurrounding("\"")?.split(',') ?: return@evaluateJavascript
                if (parts.size < 3) return@evaluateJavascript
                parts[0].toDoubleOrNull()?.let { pos.value = it }
                parts[2].toDoubleOrNull()?.takeIf { it > 0 }?.let { dur.value = it }
                when (parts[1].trim()) {
                    "1", "3" -> { endedFired = false; setPlaying(true) }
                    "0" -> ended()
                    else -> setPlaying(false)
                }
            }
        } else mp?.takeIf { mpReady }?.let {
            runCatching { pos.value = it.currentPosition / 1000.0; setPlaying(it.isPlaying) }
        }
        if (playing.value) {
            if (now - lastSave > 5000) { lastSave = now; saveNow() }
            if (lastListen > 0) Prefs.addListen(((now - lastListen) / 1000.0).coerceAtMost(2.0))
            lastListen = now
            if (now - lastStatePush > 5000) pushState(true)
        } else lastListen = 0
        if (sleepAt in 1..now) { setSleep(0); pause() }
    }

    private fun setPlaying(b: Boolean) {
        if (playing.value == b) return
        playing.value = b
        if (b) { gainFocus(); noisy(true); locks(true) } else { noisy(false); locks(false) }
        pushState(true)
    }

    // ───── الجلسة والإشعار ─────
    private fun pushMeta() {
        val id = cur.value ?: return
        val v = Repo.video(id) ?: return
        val ser = Repo.seriesOfVideo(id)
        session?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, v.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, v.speakers.joinToString("، ") { Repo.personName(it) }.ifEmpty { "ميراث" })
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, ser?.title ?: "ميراث")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (dur.value * 1000).toLong())
                .also { b -> art?.takeIf { artFor == v.yt }?.let { b.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it) } }
                .build(),
        )
        session?.isActive = true
        pushState(true)
    }

    private var lastDurPushed = 0.0
    private fun pushState(force: Boolean) {
        val s = session ?: return
        if (dur.value != lastDurPushed) { lastDurPushed = dur.value; pushMeta(); return }
        lastStatePush = System.currentTimeMillis()
        s.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_REWIND or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP,
                )
                .addCustomAction("rew", "رجوع ${SKIP.toInt()} ث", android.R.drawable.ic_media_rew)
                .addCustomAction("ff", "تقديم ${SKIP.toInt()} ث", android.R.drawable.ic_media_ff)
                .setState(
                    if (playing.value) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    (pos.value * 1000).toLong(), if (playing.value) speed.value.toFloat() else 0f,
                )
                .build(),
        )
        if (force) app?.let { PlaybackService.refresh(it) }
    }

    private fun loadArt(yt: String) {
        if (yt.isEmpty() || artFor == yt) return
        Thread {
            val bmp = runCatching {
                http.newCall(Request.Builder().url("https://i.ytimg.com/vi/$yt/hqdefault.jpg").build()).execute().use { r ->
                    r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
            }.getOrNull()
            main.post { if (bmp != null) { art = bmp; artFor = yt; pushMeta() } }
        }.start()
    }

    // ───── تركيز الصوت + فصل السماعة + أقفال الاستيقاظ ─────
    private val focusListener = AudioManager.OnAudioFocusChangeListener { ch ->
        when (ch) {
            AudioManager.AUDIOFOCUS_LOSS -> { resumeOnGain = false; pause(); focusReq = null }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { if (playing.value) { resumeOnGain = true; pause() } }
            AudioManager.AUDIOFOCUS_GAIN -> { if (resumeOnGain) { resumeOnGain = false; play() } }
        }
    }

    private fun gainFocus() {
        val ctx = app ?: return
        if (focusReq != null) return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val req = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA).setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(focusListener, main)
            .setWillPauseWhenDucked(false)
            .build()
        if (AudioManagerCompat.requestAudioFocus(am, req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focusReq = req
    }

    private fun loseFocus() {
        val ctx = app ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        focusReq?.let { AudioManagerCompat.abandonAudioFocusRequest(am, it) }
        focusReq = null
    }

    private val noisyRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { if (i?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause() }
    }

    private fun noisy(on: Boolean) {
        val ctx = app ?: return
        if (on == noisyOn) return
        noisyOn = on
        if (on) ContextCompat.registerReceiver(ctx, noisyRx, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        else runCatching { ctx.unregisterReceiver(noisyRx) }
    }

    @SuppressLint("WakelockTimeout")
    private fun locks(on: Boolean) {
        val ctx = app ?: return
        if (kind.value != "yt" && on) return
        if (on) {
            if (wake == null) wake = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mirath:play").apply { setReferenceCounted(false) }
            if (wifi == null) wifi = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .let { wm ->
                    @Suppress("DEPRECATION")
                    wm.createWifiLock(if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_HIGH_PERF else WifiManager.WIFI_MODE_FULL, "mirath:stream")
                }.apply { setReferenceCounted(false) }
            runCatching { wake?.acquire(4 * 60 * 60 * 1000L); wifi?.acquire() }
        } else runCatching { wake?.takeIf { it.isHeld }?.release(); wifi?.takeIf { it.isHeld }?.release() }
    }
}
