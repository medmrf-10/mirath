package com.irth.mirath.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irth.mirath.data.AWord
import com.irth.mirath.data.QA
import com.irth.mirath.data.Repo
import com.irth.mirath.data.Summary
import com.irth.mirath.player.YtCtl
import com.irth.mirath.player.YtPlayer
import com.irth.mirath.store.Prefs
import com.irth.mirath.ui.theme.Amiri
import com.irth.mirath.ui.theme.Pal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonScreen(p: Pal, videoId: String, onLesson: (String) -> Unit = {}) {
    val v = Repo.video(videoId)
    if (v == null) { EmptyBox(p, "الدرس غير موجود"); return }

    var mode by remember { mutableStateOf("read") }
    val words = produceState<List<AWord>?>(null, videoId) {
        value = if (v.has.contains("align")) Repo.align(videoId) else emptyList()
    }.value
    var transcript by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<Summary?>(null) }
    var questions by remember { mutableStateOf<List<QA>?>(null) }
    var pos by remember { mutableStateOf(Prefs.progress(videoId).toDouble()) }
    val ctl = remember { YtCtl() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(videoId) {
        Prefs.markOpened(videoId)
        if (v.has.contains("transcript") && !v.has.contains("align")) transcript = Repo.transcript(videoId)
        if (v.has.contains("summary")) summary = Repo.summary(videoId)
        if (v.has.contains("questions")) questions = Repo.questions(videoId)
        if (words.isNullOrEmpty() && v.has.contains("transcript")) transcript = Repo.transcript(videoId)
    }
    // احفظ التقدم كل 5 ثوان
    LaunchedEffect(videoId) {
        while (true) { delay(5000); if (pos > 0) Prefs.setProgress(videoId, pos.toInt()) }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(10.dp))
                YtPlayer(v.yt, ctl, Prefs.progress(videoId), onEnded = {
                    Prefs.setProgress(videoId, 0)
                    val ser = Repo.series?.firstOrNull { it.items.contains(videoId) }
                    if (ser != null) {
                        val idx = ser.items.indexOf(videoId)
                        val next = ser.items.drop(idx + 1).firstOrNull { nid ->
                            Repo.video(nid)?.let { !Prefs.finished(nid, it.duration) } == true
                        }
                        if (next != null) onLesson(next)
                    }
                }) { pos = it }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(v.title, color = p.text, style = MaterialTheme.typography.headlineMedium)
                        val who = v.speakers.joinToString("، ") { Repo.personName(it) }
                        if (who.isNotEmpty()) Text(who, color = p.gold, style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { Prefs.toggleFav(videoId) }) {
                        Icon(
                            if (Prefs.isFav(videoId)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "مفضلة", tint = if (Prefs.isFav(videoId)) p.gold else p.muted,
                        )
                    }
                }
                // أشرطة الأوضاع
                Row(Modifier.padding(top = 4.dp)) {
                    ModeTab(p, "قراءة", Icons.Filled.MenuBook, mode == "read") { mode = "read" }
                    if (v.has.contains("summary")) ModeTab(p, "تلخيص", Icons.Filled.ShortText, mode == "sum") { mode = "sum" }
                    if (v.has.contains("questions")) ModeTab(p, "أسئلة", Icons.Filled.Quiz, mode == "qu") { mode = "qu" }
                }
            }
        }

        when (mode) {
            "read" -> {
                val ws = words
                if (!ws.isNullOrEmpty()) {
                    val lines = ws.groupBy { it.line }.toSortedMap()
                    val cur = ws.indexOfFirst { pos >= it.s && pos < it.e }
                    itemsIndexed(lines.values.toList()) { _, line ->
                        FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            line.forEach { w ->
                                val i = ws.indexOf(w)
                                val on = i == cur
                                Text(
                                    w.w,
                                    color = if (on) p.card else p.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (on) p.primary else Color.Transparent)
                                        .clickable { ctl.seek(w.s) }
                                        .padding(horizontal = 3.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                } else {
                    item {
                        val t = transcript
                        if (t == null) LoadingBox(p)
                        else Text(
                            t, color = p.text, style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            "sum" -> item {
                val s = summary
                if (s == null) LoadingBox(p)
                else Column(Modifier.padding(16.dp)) {
                    Text(s.summary, color = p.text, style = MaterialTheme.typography.bodyLarge)
                    s.points.forEach { pt ->
                        Row(Modifier.padding(top = 8.dp)) {
                            Text("•", color = p.gold, modifier = Modifier.padding(end = 8.dp))
                            Text(pt, color = p.muted, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            "qu" -> {
                val qs = questions
                if (qs == null) item { LoadingBox(p) }
                else {
                    itemsIndexed(qs) { i, qa -> QCard(p, videoId, i, qa) }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ModeTab(p: Pal, label: String, ic: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, click: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
            .background(if (on) p.soft else Color.Transparent)
            .clickable(onClick = click)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(ic, label, tint = if (on) p.gold else p.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun QCard(p: Pal, videoId: String, i: Int, qa: QA) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp)).background(p.card).border(1.dp, p.line, RoundedCornerShape(12.dp))
            .clickable { open = !open }.padding(14.dp),
    ) {
        Row {
            Text("${i + 1}.", color = p.gold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(qa.q, color = p.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = p.muted)
        }
        if (open) {
            Divider(color = p.line, modifier = Modifier.padding(vertical = 10.dp))
            Text(qa.a, color = p.muted, style = MaterialTheme.typography.bodyLarge)
            val r = Prefs.rate(videoId, i)
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("قيّم إجابتك:", color = p.muted, fontSize = 12.sp)
                (1..5).forEach { n ->
                    IconButton(onClick = { Prefs.setRate(videoId, i, n) }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            if (n <= r) Icons.Filled.Star else Icons.Filled.StarBorder,
                            "تقييم $n", tint = if (n <= r) p.gold else p.muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
