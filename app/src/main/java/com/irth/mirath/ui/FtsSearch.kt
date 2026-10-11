package com.irth.mirath.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irth.mirath.data.Fts
import com.irth.mirath.data.Repo
import com.irth.mirath.store.Prefs
import com.irth.mirath.ui.theme.Pal

/** الدروس القابلة للفهرسة (لها محاذاة أو تفريغ) — كتالوج + هوشات + محلي. */
fun ftsCandidates(): List<String> =
    Repo.videos?.filter { it.has.contains("align") || it.has.contains("transcript") }?.map { it.id } ?: emptyList()

/** دروس السلاسل التي بدأها المستخدم + مفضلته — فهرسة سريعة لما يهمه. */
fun ftsMine(): List<String> {
    val started = Repo.series?.filter { s -> s.items.any { Prefs.progress(it) > 10 || Prefs.isFav(it) } } ?: emptyList()
    val c = ftsCandidates().toSet()
    return (started.flatMap { it.items } + Prefs.favs.value + Prefs.recent.value).filter { it in c }.distinct()
}

/** شريط تغطية الفهرس: كم درساً يمكن البحث في كلامه + فهرسة بالخلفية مع تقدم وإلغاء. */
@Composable
fun FtsCoverage(p: Pal) {
    val all = ftsCandidates()
    val n = Fts.count.value
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.soft)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ManageSearch, null, tint = p.gold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "البحث في كلام الدروس: ${n.coerceAtMost(all.size.coerceAtLeast(n))} من ${all.size} درساً مفهرس",
                color = p.text, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
        }
        if (Fts.busy.value) {
            Spacer(Modifier.height(6.dp))
            val f = if (Fts.total.value > 0) Fts.done.value.toFloat() / Fts.total.value else 0f
            LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().height(3.dp), color = p.gold, trackColor = p.card)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("يفهرس ${Fts.done.value}/${Fts.total.value} — يستمر لو أغلقت", color = p.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { Fts.cancel() }) { Text("إيقاف", color = p.muted, fontSize = 12.sp) }
            }
        } else if (n < all.size) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val mine = ftsMine().filter { !Fts.has(it) }
                if (mine.isNotEmpty()) TextButton(onClick = { Fts.indexAll(mine) }) {
                    Text("فهرس سلاسلي (${mine.size})", color = p.gold, fontSize = 12.sp)
                }
                TextButton(onClick = { Fts.indexAll(all) }) {
                    Text("فهرس المكتبة كلها", color = p.primary, fontSize = 12.sp)
                }
            }
        }
    }
}

/** قسم نتائج «في كلام الدروس»: درس ← مواضع موقوتة بمقتطف مظلّل ← نقرة تقفز للثانية. */
fun LazyListScope.ftsSection(p: Pal, groups: List<Fts.Group>, onTimed: (String, Int) -> Unit) {
    if (groups.isEmpty()) return
    item { SectionTitle(p, "في كلام الدروس — ${groups.size} درساً") }
    items(groups, key = { "fts:" + it.id }) { g ->
        val v = Repo.video(g.id)
        val ser = Repo.seriesOfVideo(g.id)
        Column(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                .background(p.bg).padding(10.dp),
        ) {
            Text(v?.title ?: g.id, color = p.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ser != null) Text(ser.title, color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            g.hits.forEach { h ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp)).background(p.card)
                        .clickable { onTimed(h.id, h.sec.coerceAtLeast(0)) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = p.gold, modifier = Modifier.size(18.dp))
                    Text(
                        (if (h.approx) "≈" else "") + fmtDur(h.sec.coerceAtLeast(0)),
                        color = p.gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp, end = 8.dp),
                    )
                    Text(snippet(p, h), fontSize = 13.sp, lineHeight = 20.sp, color = p.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun snippet(p: Pal, h: Fts.Hit) = buildAnnotatedString {
    val t = h.text
    val a = h.at.coerceIn(0, t.length); val b = (h.at + h.len).coerceIn(a, t.length)
    val from = (a - 45).coerceAtLeast(0).let { s -> t.lastIndexOf(' ', s).takeIf { it in 0 until a }?.plus(1) ?: s }
    val to = (b + 70).coerceAtMost(t.length)
    if (from > 0) append("…")
    append(t.substring(from, a))
    withStyle(SpanStyle(color = p.gold, fontWeight = FontWeight.Bold, background = p.soft)) { append(t.substring(a, b)) }
    append(t.substring(b, to))
    if (to < t.length) append("…")
}
