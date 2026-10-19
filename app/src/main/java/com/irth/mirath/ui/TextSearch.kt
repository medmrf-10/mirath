package com.irth.mirath.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irth.mirath.data.Repo
import com.irth.mirath.data.TextIndex
import com.irth.mirath.store.Prefs
import com.irth.mirath.ui.theme.Pal
import kotlinx.coroutines.delay

private const val SEP = "\u0001"

private fun recentQs(): List<String> = Prefs.str("tq.recent").split(SEP).filter { it.isNotBlank() }
private fun pushRecent(q: String) {
    val l = (listOf(q) + recentQs().filter { it != q }).take(8)
    Prefs.putStr("tq.recent", l.joinToString(SEP))
}

private fun fmtS(s: Double): String {
    val t = s.toInt()
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
}

/** البحث داخل نصوص المكتبة كلها — نتائج موقوتة مجمّعة بالدرس، والضغط يفتح الدرس عند الثانية المطابقة. */
@Composable
fun TextSearchPane(p: Pal, q: String, onQuery: (String) -> Unit, onHit: (String, Int) -> Unit) {
    val t = q.trim()
    val cnt by TextIndex.count
    val running by TextIndex.running
    val done by TextIndex.done
    val total by TextIndex.total
    val rev by TextIndex.rev
    val eligible = remember(cnt) { TextIndex.eligible() }
    var res by remember { mutableStateOf<List<TextIndex.Group>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(setOf<String>()) }
    var recents by remember { mutableStateOf(recentQs()) }

    LaunchedEffect(t, running, if (running) done / 80 else rev) {
        if (t.length < 2) { res = null; busy = false; return@LaunchedEffect }
        delay(250)
        busy = true
        try { res = TextIndex.search(t) } finally { busy = false }
    }

    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.bg)
                    .border(1.dp, p.line, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ManageSearch, null, tint = p.gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    val full = cnt >= eligible && eligible > 0
                    Text(
                        when {
                            running -> "يفهرس الدروس… $done / $total"
                            full -> "كل المكتبة مفهرسة — $cnt درساً"
                            else -> "مفهرس $cnt من $eligible درساً — يُفهرس تلقائياً ما تفتحه أو تنزّله"
                        },
                        color = p.text, fontSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                    if (running) TextButton(onClick = { TextIndex.stop() }) { Text("إيقاف", color = p.muted) }
                    else if (!full) TextButton(onClick = { TextIndex.buildAll() }) { Text("فهرس دفعة", color = p.primary, fontWeight = FontWeight.Bold) }
                }
                if (running && total > 0) {
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(4.dp)),
                        color = p.gold, trackColor = p.soft,
                    )
                    Text("يستمر في الخلفية — أغلق البحث وتابع الاستماع", color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (t.length < 2) {
            if (recents.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("عمليات بحث سابقة", color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { Prefs.putStr("tq.recent", ""); recents = emptyList() }) { Text("مسح", color = p.muted, fontSize = 12.sp) }
                    }
                }
                items(recents) { r ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable { onQuery(r) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.History, null, tint = p.muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(r, color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    }
                }
            } else item {
                Text(
                    "اكتب كلمة أو عبارة سمعتها — نجد موضعها في كل الدروس المفهرسة ونفتح الدرس عند الثانية نفسها. التشكيل والهمزات لا تؤثر.",
                    color = p.muted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(8.dp),
                )
            }
            return@LazyColumn
        }

        val r = res
        if (r == null) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.gold, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) } }
            return@LazyColumn
        }
        if (r.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.SearchOff, null, tint = p.muted, modifier = Modifier.size(32.dp))
                    Text("لا مواضع لـ«$t» في النصوص المفهرسة", color = p.muted, modifier = Modifier.padding(top = 8.dp))
                    if (cnt < eligible && !running) {
                        Button(
                            onClick = { TextIndex.buildAll() },
                            modifier = Modifier.padding(top = 10.dp).heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = p.primary, contentColor = p.card),
                        ) { Text("فهرس دفعة إضافية من المكتبة") }
                    } else Text("جرّب كلمة أقصر أو جذراً منها", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            return@LazyColumn
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text("${r.sumOf { it.total }} موضعاً في ${r.size} درساً", color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (busy) CircularProgressIndicator(color = p.gold, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            }
        }
        r.forEach { g ->
            val v = Repo.video(g.id)
            item(key = "h" + g.id) {
                Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(v?.title ?: g.id, color = p.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${g.total}", color = p.gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(p.soft).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    Repo.seriesOfVideo(g.id)?.title?.let { Text(it, color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            val shown = if (g.id in open) g.hits else g.hits.take(3)
            items(shown, key = { h -> "x" + g.id + h.s + h.at }) { h ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(p.card)
                        .border(1.dp, p.line, RoundedCornerShape(12.dp))
                        .clickable { pushRecent(t); recents = recentQs(); onHit(g.id, if (h.s < 0) -1 else (h.s - 1).toInt().coerceAtLeast(0)) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (h.s < 0) "—" else (if (h.approx) "≈" else "") + fmtS(h.s),
                        color = p.gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 44.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    val a = h.at.coerceIn(0, h.snippet.length); val b = (h.at + h.len).coerceIn(a, h.snippet.length)
                    Text(
                        buildAnnotatedString {
                            append(h.snippet.substring(0, a))
                            pushStyle(SpanStyle(color = p.gold, fontWeight = FontWeight.Bold, background = p.soft))
                            append(h.snippet.substring(a, b)); pop()
                            append(h.snippet.substring(b))
                        },
                        color = p.text, fontSize = 13.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (g.hits.size > 3 || g.total > g.hits.size) item(key = "m" + g.id) {
                val isOpen = g.id in open
                Text(
                    if (isOpen) "أقل" else "كل المواضع (${g.total})",
                    color = p.primary, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = if (isOpen) open - g.id else open + g.id }.padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
