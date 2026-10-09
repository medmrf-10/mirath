package com.irth.mirath.player

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** مقبض المشغّل — تنادي seek للقفز في الفيديو. */
class YtCtl { var seek: (Double) -> Unit = {} }

/** مشغّل يوتيوب مضمّن — نفس أسلوب موقع إرث (iframe API) مع جسر موضعٍ للمحاذاة. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtPlayer(
    videoId: String,
    ctl: YtCtl,
    startSec: Int = 0,
    onTime: (Double) -> Unit,
) {
    val webView = remember { mutableListOf<WebView?>(null) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(videoId) {
        ctl.seek = { sec -> webView[0]?.evaluateJavascript("try{pl&&pl.seekTo($sec,true)}catch(e){}", null) }
        onDispose { ctl.seek = {} }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                webView[0] = this
                loadDataWithBaseURL(
                    "https://www.youtube-nocookie.com",
                    """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;background:#000;height:100%}#p{position:fixed;inset:0;width:100%;height:100%}</style></head>
<body><div id="p"></div><script>
var pl;window.ct=0;
function onYouTubeIframeAPIReady(){pl=new YT.Player('p',{videoId:'$videoId',
playerVars:{rel:0,playsinline:1,enablejsapi:1,start:$startSec},
events:{onStateChange:function(e){if(e.data==1)window.ct=pl.getCurrentTime()}}});}
setInterval(function(){try{if(pl&&pl.getCurrentTime)window.ct=pl.getCurrentTime()}catch(e){}},200);
</script><script src="https://www.youtube.com/iframe_api"></script></body></html>""",
                    "text/html", "utf-8", null,
                )
            }
        },
    )

    DisposableEffect(videoId) {
        val tick = object : Runnable {
            override fun run() {
                webView[0]?.evaluateJavascript("window.ct") { v ->
                    v?.removeSurrounding("\"")?.toDoubleOrNull()?.let(onTime)
                }
                handler.postDelayed(this, 300)
            }
        }
        handler.postDelayed(tick, 300)
        onDispose {
            handler.removeCallbacks(tick)
            webView[0]?.destroy()
        }
    }
}
