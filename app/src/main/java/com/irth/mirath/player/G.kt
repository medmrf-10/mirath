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
import kotlinx.coroutines.launch
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
    /** سياق الطابور: السلسلة والشيخ والتالي/السابق — يقرؤها الإشعار والمشغّل المصغّر. */
    var seriesTitle by mutableStateOf("")
    var artist by mutableStateOf("")
    var scienceId by mutableStateOf("")
    var nextId by mutableStateOf<String?>(null)
    var prevId by mutableStateOf<String?>(null)
    /** مؤقت النوم العالمي: لحظة الانتهاء (ms) أو 0، أو «نهاية الدرس». يعيش خارج أي شاشة. */
    var sleepAt by mutableStateOf(0L)
    var sleepEnd by mutableStateOf(false)

    var webView: WebView? = null
        private set
    private var mp: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var endedFired = false
    private var lastFinishedId: String? = null
    private var pausedAtMs: Long = 0L
    var onEnded: () -> Unit = {}
    var onTime: (Double) -> Unit = {}
    private var ctxRef: Context? = null
    private var focusReq: AudioManager.OnAudioFocusChangeListener? = null

    private const val YT_PAGE = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;background:#000;height:100%;overflow:hidden}#p{position:fixed;inset:0;width:100%;height:100%;border:0}</style></head>
<body><div id="p"></div><script>
var pl=null,rdy=false,retried={};window.ct=0;window.ps=-1;
function loadApi(){
  if(window.YT&&window.YT.Player)return;
  var old=document.getElementById('yt-api');if(old)old.remove();
  var s=document.createElement('script');s.id='yt-api';
  s.src='https://www.youtube.com/iframe_api';
  s.onerror=function(){setTimeout(loadApi,1800);};
  document.body.appendChild(s);
}
window.loadVid=function(id,st){
  if(!id)return;
  var s=st||0;
  window.__cur={id:id,st:s};
  if(!rdy||!pl||typeof pl.loadVideoById!=='function'){
    window.__pend={id:id,st:s};
    loadApi();
    return;
  }
  window.ct=s;window.ps=-1;
  try{pl.loadVideoById({videoId:id,startSeconds:s});}catch(e){window.__pend={id:id,st:s};}
};
function onYouTubeIframeAPIReady(){
  pl=new YT.Player('p',{
    width:'100%',height:'100%',
    playerVars:{rel:0,playsinline:1,enablejsapi:1,origin:'https://www.youtube.com'},
    events:{
      onReady:function(){
        rdy=true;
        if(window.__pend){var p=window.__pend;window.__pend=null;window.loadVid(p.id,p.st);}
      },
      onStateChange:function(e){window.ps=e.data;},
      onError:function(e){
        var c=window.__cur;
        if(c&&c.id){
          var n=(retried[c.id]||0)+1;retried[c.id]=n;
          if(n<=2){
            setTimeout(function(){
              try{pl.loadVideoById({videoId:c.id,startSeconds:Math.floor(window.ct||c.st||0)});}catch(x){}
            },600*n);
          }
        }
      }
    }
  });
}
setInterval(function(){try{if(rdy&&pl&&pl.getCurrentTime)window.ct=pl.getCurrentTime()}catch(e){}},200);
loadApi();
</script></body></html>"""

    fun ensureWeb(ctx: Context): WebView {
        webView?.let { return it }
        val appCtx = ctx.applicationContext
        return WebView(appCtx).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            setBackgroundColor(android.graphics.Color.BLACK)
            loadDataWithBaseURL("https://www.youtube.com", YT_PAGE, "text/html", "utf-8", null)
        }.also { webView = it }
    }

    fun toggle() {
        if (playing) pause() else play()
    }
    fun playPause() = toggle()
    fun play() {
        // Smart 2-second rewind after a pause longer than 60 seconds
        if (pausedAtMs > 0L && System.currentTimeMillis() - pausedAtMs > 60_000L && pos > 3.0) {
            val rewindTo = (pos - 2.0).coerceAtLeast(0.0)
            pos = rewindTo
            if (local) runCatching { mp?.seekTo((rewindTo * 1000).toInt()) }
            else webView?.evaluateJavascript("try{rdy&&pl&&pl.seekTo($rewindTo,true)}catch(e){}", null)
        }
        pausedAtMs = 0L
        if (local) runCatching { mp?.start() }
        else webView?.evaluateJavascript("try{rdy&&pl&&pl.playVideo()}catch(e){}", null)
    }
    fun pause() {
        pausedAtMs = System.currentTimeMillis()
        saveProgress()
        if (local) runCatching { mp?.pause() }
        else webView?.evaluateJavascript("try{rdy&&pl&&pl.pauseVideo()}catch(e){}", null)
    }
    fun seek(sec: Double) {
        pos = sec.coerceAtLeast(0.0)
        if (local) runCatching { mp?.seekTo((pos * 1000).toInt()) }
        else webView?.evaluateJavascript("try{if(rdy&&pl){pl.seekTo($pos,true);pl.playVideo()}}catch(e){}", null)
    }
    fun setRate(v: Double) {
        speed = v
        runCatching { com.irth.mirath.store.Prefs.setSpeed(v.toFloat()) }
        if (local) runCatching { mp?.playbackParams = mp?.playbackParams?.setSpeed(v.toFloat())!! }
        else webView?.evaluateJavascript("try{rdy&&pl&&pl.setPlaybackRate($v)}catch(e){}", null)
    }
    fun pauseAll() {
        pausedAtMs = System.currentTimeMillis()
        runCatching { mp?.pause() }
        webView?.evaluateJavascript("try{rdy&&pl&&pl.pauseVideo()}catch(e){}", null)
    }
    fun stopAll() {
        saveProgress()
        sleepAt = 0L; sleepEnd = false; pausedAtMs = 0L
        runCatching { mp?.stop(); mp?.release() }
        mp = null
        webView?.evaluateJavascript("try{rdy&&pl&&pl.stopVideo()}catch(e){}", null)
        videoId = null; playing = false; pos = 0.0
        ctxRef?.let { it.stopService(Intent(it, PlayerService::class.java)) }
    }

    fun play(ctx: Context, id: String, name: String, ytId: String, media: File?, startSec: Int, ended: () -> Unit) {
        ctxRef = ctx.applicationContext
        runCatching { com.irth.mirath.store.Prefs.touchActivity() }
        if (videoId == id && (media != null || ytId.isNotEmpty())) { // نفس الدرس — أعد ربط المستمعين فقط، أو اقفز للموضع المطلوب إن تغيّر
            onEnded = ended
            if (startSec > 0 && kotlin.math.abs(pos - startSec) > 3.0) seek(startSec.toDouble())
            return
        }
        saveProgress()
        endedFired = false
        lastFinishedId = null
        pausedAtMs = 0L
        videoId = id; title = name; onEnded = ended
        speed = runCatching { com.irth.mirath.store.Prefs.speed().toDouble() }.getOrDefault(speed)
        resolveContext(id)
        lastRefresh = 0L; ytLive = false
        dur = 0.0; pos = startSec.toDouble()
        val svc = Intent(ctx, PlayerService::class.java)
        if (media != null) {
            // مسار محلي: أوقف يوتيوب، شغّل MediaPlayer
            webView?.evaluateJavascript("try{rdy&&pl&&pl.stopVideo()}catch(e){}", null)
            local = true
            runCatching { mp?.release() }
            mp = MediaPlayer().apply {
                runCatching {
                    setDataSource(media.absolutePath)
                    setOnPreparedListener {
                        if (startSec > 0) seekTo(startSec * 1000)
                        start(); playing = true
                    }
                    setOnCompletionListener { playing = false; finishLesson() }
                    setOnErrorListener { _, _, _ -> playing = false; true }
                    prepareAsync()
                }
            }
        } else {
            runCatching { mp?.pause() }
            local = false
            val wv = ensureWeb(ctx)
            if (ytId.isNotEmpty()) {
                wv.evaluateJavascript("window.loadVid('$ytId',$startSec)", null)
            } else if (id.startsWith("H:") && id.contains(":")) {
                val sid = id.substringBeforeLast(":")
                com.irth.mirath.data.Haw.bg.launch(kotlinx.coroutines.Dispatchers.IO) {
                    com.irth.mirath.data.Haw.ensure(sid)
                    val fresh = com.irth.mirath.data.Repo.video(id)
                    if (fresh != null && fresh.yt.isNotEmpty() && videoId == id) {
                        handler.post {
                            title = fresh.title
                            wv.evaluateJavascript("window.loadVid('${fresh.yt}',$startSec)", null)
                        }
                    }
                }
            }
        }
        if (speed != 1.0) { val r = speed; handler.postDelayed({ setRate(r) }, 900) }
        runCatching { ctx.startForegroundService(svc) }
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
                        ytLive = st == 1 || st == 2
                        if (st == 0 && !endedFired) { endedFired = true; finishLesson() }
                        if (st == 1) { endedFired = false; lastFinishedId = null }
                    }
                    webView?.evaluateJavascript("try{pl?pl.getDuration():0}catch(e){0}") { v ->
                        v?.removeSurrounding("\"")?.toDoubleOrNull()?.let { if (it > 0) dur = it }
                    }
                }
                heartbeat()
                if (videoId != null) handler.postDelayed(this, 300) else ticking = false
            }
        })
    }

    // ———————————— محرك الطابور: يعمل بلا أي شاشة ————————————

    private var ytLive = false
    private var lastRefresh = 0L
    private var lastSave = 0L
    private var lastPlaying = false

    /** يشغّل درساً بمعرّفه فقط: يحلّ المصدر (محلي/يوتيوب) وموضع الاستئناف من المخزن — مدخل الإشعار والطابور والشاشات. */
    fun playLesson(ctx: Context, id: String): Boolean {
        val v = com.irth.mirath.data.Repo.video(id) ?: return false
        val ser = com.irth.mirath.data.Repo.seriesOfVideo(id)
        val media = com.irth.mirath.data.LocalLib.mediaFor(id)
            ?.takeIf { v.yt.isEmpty() || com.irth.mirath.store.Prefs.src(ser?.id ?: "") == "local" }
        if (media == null && v.yt.isEmpty() && !id.startsWith("H:")) return false
        val P = com.irth.mirath.store.Prefs
        val start = if (P.finished(id, v.duration)) 0 else P.progress(id)
        play(ctx, id, v.title, v.yt, media, start) {}
        return true
    }

    private fun resolveContext(id: String) {
        val R = com.irth.mirath.data.Repo
        val v = R.video(id)
        val ser = R.seriesOfVideo(id)
        seriesTitle = ser?.title ?: ""
        scienceId = ser?.science ?: ""
        artist = v?.speakers?.joinToString("، ") { R.personName(it) }?.ifEmpty { null }
            ?: R.sciences?.firstOrNull { it.id == ser?.science }?.name ?: "ميراث"
        val items = ser?.items ?: emptyList()
        val i = items.indexOf(id)
        prevId = if (i > 0) items[i - 1] else null
        nextId = if (i >= 0) items.drop(i + 1).firstOrNull { nid ->
            R.video(nid)?.let { !com.irth.mirath.store.Prefs.finished(nid, it.duration) } == true
        } ?: items.getOrNull(i + 1) else null
    }

    /** نهاية طبيعية: احفظ «مكتمل»، احترم مؤقت «نهاية الدرس»، وإلا انتقل وحدك للتالي غير المكتمل. */
    private fun finishLesson() {
        val id = videoId ?: return
        if (lastFinishedId == id) return
        lastFinishedId = id
        val full = (com.irth.mirath.data.Repo.video(id)?.duration ?: 0).coerceAtLeast(dur.toInt())
        runCatching { com.irth.mirath.store.Prefs.setProgress(id, full) }
        lastSave = System.currentTimeMillis()
        onEnded()
        if (sleepEnd) { sleepEnd = false; playing = false; pauseAll(); heartbeat(force = true); return }
        val ctx = ctxRef ?: return
        val nx = nextId
        if (nx == null || !playLesson(ctx, nx)) { playing = false; heartbeat(force = true) }
    }

    fun skipNext(): Boolean {
        val ctx = ctxRef ?: return false
        val nx = nextId ?: return false
        return playLesson(ctx, nx)
    }

    /** السابق كأنضج المشغّلات: بعد ٥ ثوانٍ يعيد الدرس من أوله، قبلها يرجع للدرس السابق. */
    fun skipPrev(): Boolean {
        val ctx = ctxRef ?: return false
        if (pos > 5 || prevId == null) { seek(0.0); return true }
        return playLesson(ctx, prevId!!)
    }

    /** دورة مؤقت النوم: إيقاف ← 10 ← 20 ← 30 ← 45 ← نهاية الدرس ← إيقاف. */
    fun cycleSleep() {
        val now = System.currentTimeMillis()
        val left = if (sleepAt > 0) ((sleepAt - now) / 60000.0) else -1.0
        val steps = listOf(10, 20, 30, 45)
        when {
            sleepEnd -> { sleepEnd = false; sleepAt = 0L }
            sleepAt == 0L -> sleepAt = now + steps[0] * 60_000L
            else -> {
                val nx = steps.firstOrNull { it > left + 0.5 }
                if (nx != null) sleepAt = now + nx * 60_000L else { sleepAt = 0L; sleepEnd = true }
            }
        }
    }

    /** نص مختصر لحالة المؤقت للشاشات: «نوم 9د» أو «حتى نهايته». */
    fun sleepLabel(): String? = when {
        sleepEnd -> "نوم: آخر الدرس"
        sleepAt > 0 -> "نوم " + (((sleepAt - System.currentTimeMillis()) / 60000) + 1).coerceAtLeast(1) + "د"
        else -> null
    }

    fun saveProgress() {
        val id = videoId ?: return
        if (pos > 1 && (local || ytLive)) runCatching { com.irth.mirath.store.Prefs.setProgress(id, pos.toInt()) }
        lastSave = System.currentTimeMillis()
    }

    /** نبض الحلقة: حفظ التقدّم كل ٥ث، مؤقت النوم، وإنعاش الإشعار فقط عند تغيّر الحالة أو كل ٥ث (لا ٣ مرات/ث). */
    private fun heartbeat(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (playing && now - lastSave > 5000) saveProgress()
        if (sleepAt in 1..now) { sleepAt = 0L; pauseAll(); playing = false; saveProgress() }
        if (force || playing != lastPlaying || now - lastRefresh > 5000) {
            lastPlaying = playing; lastRefresh = now
            PlayerService.refresh(ctxRef)
        }
    }
}
