package com.irth.mirath.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * المشغّل العالمي الوحيد — يعيش خارج أي شاشة.
 * يوتيوب: WebView خفي واحد يُعاد توجيهه بين الدروس (يظهر داخل صفحة الدرس النشطة).
 * محلي: MediaPlayer مباشر. PlaybackService يبقي العملية حية (إشعار أمامي).
 */
@SuppressLint("SetJavaScriptEnabled")
object G {
    var videoId by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var playing by mutableStateOf(false)
    var pos by mutableStateOf(0.0)
    var dur by mutableStateOf(0.0)
    var local by mutableStateOf(false)
    var speed by mutableStateOf(1.0)

    var webView: WebView? = null
        private set
    private var mp: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var endedFired = false
    var onEnded: () -> Unit = {}
    var onTime: (Double) -> Unit = {}
    private var ctxRef: Context? = null
    private var focusReq: AudioManager.OnAudioFocusChangeListener? = null

    private const val YT_PAGE = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;background:#000;height:100%}#p{position:fixed;inset:0;width:100%;height:100%}</style></head>
<body><div id="p"></div><script>
var pl;window.ct=0;window.ps=-1;
function onYouTubeIframeAPIReady(){pl=new YT.Player('p',{playerVars:{rel:0,playsinline:1,enablejsapi:1},
events:{onReady:function(){if(window.__pend){loadVid(window.__pend.id,window.__pend.st);window.__pend=null}},
onStateChange:function(e){window.ps=e.data;}}});}
window.loadVid=function(id,st){try{pl.loadVideoById({videoId:id,startSeconds:st})}catch(e){}};
setInterval(function(){try{if(pl&&pl.getCurrentTime)window.ct=pl.getCurrentTime()}catch(e){}},200);
</script><script src="https://www.youtube.com/iframe_api"></script></body></html>"""

    private fun ensureWeb(ctx: Context) {
        if (webView != null) return
        webView = WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            loadDataWithBaseURL("https://www.youtube-nocookie.com", YT_PAGE, "text/html", "utf-8", null)
        }
    }

    fun toggle() {
        if (local) runCatching { if (mp?.isPlaying == true) mp?.pause() else mp?.start() }
        else webView?.evaluateJavascript("try{window.ps==1?pl.pauseVideo():pl.playVideo()}catch(e){}", null)
    }
    fun playPause() = toggle()
    fun play() {
        if (local) runCatching { mp?.start() }
        else webView?.evaluateJavascript("try{pl&&pl.playVideo()}catch(e){}", null)
    }
    fun pause() {
        if (local) runCatching { mp?.pause() }
        else webView?.evaluateJavascript("try{pl&&pl.pauseVideo()}catch(e){}", null)
    }
    fun seek(sec: Double) {
        if (local) runCatching { mp?.seekTo((sec * 1000).toInt()) }
        else webView?.evaluateJavascript("try{pl&&pl.seekTo($sec,true);pl.playVideo()}catch(e){}", null)
    }
    fun setRate(v: Double) {
        speed = v
        if (local) runCatching { mp?.playbackParams = mp?.playbackParams?.setSpeed(v.toFloat())!! }
        else webView?.evaluateJavascript("try{pl&&pl.setPlaybackRate($v)}catch(e){}", null)
    }
    fun pauseAll() {
        runCatching { mp?.pause() }
        webView?.evaluateJavascript("try{pl&&pl.pauseVideo()}catch(e){}", null)
    }
    fun stopAll() {
        runCatching { mp?.stop(); mp?.release() }
        mp = null
        webView?.evaluateJavascript("try{pl&&pl.stopVideo()}catch(e){}", null)
        videoId = null; playing = false; pos = 0.0
        ctxRef?.let { it.stopService(Intent(it, PlayerService::class.java)) }
    }

    fun play(ctx: Context, id: String, name: String, ytId: String, media: File?, startSec: Int, ended: () -> Unit) {
        ctxRef = ctx.applicationContext
        runCatching { com.irth.mirath.store.Prefs.touchActivity() }
        if (videoId == id) { // نفس الدرس — أعد ربط المستمعين فقط
            onEnded = ended; return
        }
        endedFired = false
        videoId = id; title = name; onEnded = ended
        dur = 0.0; pos = startSec.toDouble()
        val svc = Intent(ctx, PlayerService::class.java)
        if (media != null) {
            // مسار محلي: أوقف يوتيوب، شغّل MediaPlayer
            webView?.evaluateJavascript("try{pl&&pl.stopVideo()}catch(e){}", null)
            local = true
            runCatching { mp?.release() }
            mp = MediaPlayer().apply {
                runCatching {
                    setDataSource(media.absolutePath)
                    setOnPreparedListener {
                        if (startSec > 0) seekTo(startSec * 1000)
                        start(); playing = true
                    }
                    setOnCompletionListener { playing = false; ended() }
                    setOnErrorListener { _, _, _ -> playing = false; true }
                    prepareAsync()
                }
            }
        } else {
            runCatching { mp?.pause() }
            local = false
            ensureWeb(ctx)
            // pl قد لا يكون جاهزاً بعد — __pend يلتقطه onReady داخل الصفحة
            webView?.evaluateJavascript("try{pl&&pl.loadVideoById?loadVid('$ytId',$startSec):(window.__pend={id:'$ytId',st:$startSec})}catch(e){window.__pend={id:'$ytId',st:$startSec}}", null)
        }
        if (speed != 1.0) { val r = speed; handler.postDelayed({ setRate(r) }, 900) }
        ctx.startForegroundService(svc)
        grabFocus()
        tick()
    }

    private fun grabFocus() {
        val am = ctxRef?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (focusReq == null) {
            focusReq = AudioManager.OnAudioFocusChangeListener { c ->
                if (c == AudioManager.AUDIOFOCUS_LOSS || c == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pauseAll()
            }
        }
        runCatching {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusReq, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private var ticking = false
    private fun tick() {
        if (ticking) return
        ticking = true
        handler.post(object : Runnable {
            override fun run() {
                if (local) {
                    runCatching {
                        pos = (mp?.currentPosition ?: 0) / 1000.0
                        dur = (mp?.duration ?: 0) / 1000.0
                        playing = mp?.isPlaying == true
                    }
                    onTime(pos)
                } else {
                    webView?.evaluateJavascript("window.ct") { v ->
                        v?.removeSurrounding("\"")?.toDoubleOrNull()?.let { pos = it; onTime(it) }
                    }
                    webView?.evaluateJavascript("window.ps") { v ->
                        val st = v?.trim()?.toIntOrNull() ?: -1
                        playing = st == 1
                        if (st == 0 && !endedFired) { endedFired = true; onEnded() }
                        if (st == 1) endedFired = false
                    }
                }
                PlayerService.refresh(ctxRef)
                if (videoId != null) handler.postDelayed(this, 300) else ticking = false
            }
        })
    }
}
