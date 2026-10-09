package com.irth.mirath.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irth.mirath.data.Repo
import com.irth.mirath.store.Prefs
import com.irth.mirath.ui.theme.Pal

private fun progressBar(id: String, duration: Int): Float {
    val pv = Prefs.progress(id)
    return if (pv > 10 && duration > 0) (pv.toFloat() / duration).coerceAtMost(0.98f) else 0f
}

@Composable
fun HomeScreen(p: Pal, onScience: (String) -> Unit, onLesson: (String) -> Unit) {
    val sciences = Repo.sciences
    if (sciences == null) { LoadingBox(p); return }
    val lastId = Prefs.last()
    val lastV = lastId?.let { Repo.video(it) }
    val recent = Prefs.recent.value.mapNotNull { Repo.video(it) }.filter { Prefs.progress(it.id) > 10 }.take(4)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("العلوم", style = MaterialTheme.typography.displayLarge, color = p.text)
                Text("اختر علماً لتصف سلاسله", color = p.muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (lastV != null && Prefs.ongoing(lastV.id, lastV.duration)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(16.dp)).background(p.card).border(1.dp, p.gold, RoundedCornerShape(16.dp))
                        .clickable { onLesson(lastV.id) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = p.gold, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("أكمل المشاهدة", color = p.gold, style = MaterialTheme.typography.labelMedium)
                        Text(lastV.title, color = p.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val left = (lastV.duration - Prefs.progress(lastV.id)).coerceAtLeast(0)
                        if (left > 0) Text("متبقٍ ${fmtDur(left)}", color = p.muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                SectionTitle(p, "آخر ما شغّلت")
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    recent.forEach { v ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(p.card)
                                .border(1.dp, p.line, RoundedCornerShape(14.dp))
                                .clickable { onLesson(v.id) }.padding(8.dp),
                        ) {
                            Box(
                                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp)).background(p.soft),
                                contentAlignment = Alignment.Center,
                            ) { Text("${v.title.firstOrNull() ?: "؟"}", color = p.gold, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
                            Text(v.title, color = p.muted, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).height(32.dp))
                            Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(3.dp).clip(RoundedCornerShape(3.dp)).background(p.soft)) {
                                Box(Modifier.fillMaxWidth(progressBar(v.id, v.duration)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(p.gold))
                            }
                        }
                    }
                    repeat(4 - recent.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        item { SectionTitle(p, "العلوم") }
        itemsIndexed(sciences) { i, s ->
            RowCard(p, i, s.name, onClick = { onScience(s.id) }, leading = { PhIcon(p, s.name.first().toString()) })
        }
    }
}

@Composable
fun ScienceScreen(p: Pal, scienceId: String, onSeries: (String) -> Unit, onLesson: (String) -> Unit) {
    val science = Repo.sciences?.firstOrNull { it.id == scienceId }
    val series = Repo.seriesOfScience(scienceId)
    val works = Repo.worksOfScience(scienceId)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(science?.name ?: "", style = MaterialTheme.typography.displayLarge, color = p.text)
            }
        }
        if (works.isNotEmpty()) {
            item { SectionTitle(p, "المصنفات") }
            itemsIndexed(works) { i, w ->
                RowCard(p, i, w.title, subtitle = Repo.personName(w.author), onClick = {})
            }
        }
        item { SectionTitle(p, "السلاسل") }
        itemsIndexed(series) { i, s ->
            val n = s.items.size
            val nw = s.items.count { Prefs.progress(it) <= 10 }
            val finN = s.items.count { vid -> Repo.video(vid)?.let { Prefs.finished(vid, it.duration) } == true }
            val wp = if (n > 0) finN * 100 / n else 0
            val tag = when {
                wp == 100 -> "اكتملت ✓"
                nw == n -> "لم تشاهدها"
                nw > 0 -> pl3(nw, "درس جديد", "درسان جديدان", "دروس جديدة", "درساً جديداً")
                else -> ""
            }
            val sheikh = s.items.firstOrNull()?.let { Repo.video(it)?.speakers?.firstOrNull() }?.let { Repo.personName(it) } ?: ""
            RowCard(
                p, i, s.title,
                meta = listOf(pl3(n, "درس", "درسان", "دروس", "درساً"), sheikh, s.kind).filter { it.isNotEmpty() }.joinToString(" · "),
                tag = tag,
                bar = if (wp in 1..99) wp / 100f else 0f,
                leading = { PhIcon(p, s.title.first().toString()) },
                onClick = { onSeries(s.id) },
            )
        }
        if (series.isEmpty()) item { EmptyBox(p, "لا سلاسل بعد") }
    }
}

@Composable
fun SeriesScreen(p: Pal, seriesId: String, onLesson: (String) -> Unit) {
    val s = Repo.seriesOf(seriesId)
    if (s == null) { EmptyBox(p, "السلسلة غير موجودة"); return }
    val ctx = LocalContext.current
    val cont = s.items.firstOrNull { vid -> Repo.video(vid)?.let { Prefs.ongoing(vid, it.duration) } == true }
    val finN = s.items.count { vid -> Repo.video(vid)?.let { Prefs.finished(vid, it.duration) } == true }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.title, style = MaterialTheme.typography.displayLarge, color = p.text)
                Text(pl3(s.items.size, "درس", "درسان", "دروس", "درساً"), color = p.gold, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (finN > 0) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("شاهدت ${pl3(finN, "درساً واحداً", "درسين", "دروساً", "درساً")} من ${s.items.size}", color = p.muted, fontSize = 13.sp)
                    Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(4.dp)).background(p.soft)) {
                        Box(Modifier.fillMaxWidth(finN.toFloat() / s.items.size).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(p.gold))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onLesson(cont ?: s.items.first()) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = p.gold)
                    Spacer(Modifier.width(6.dp))
                    Text(if (cont != null) "أكمل من حيث توقفت" else "استمع من البداية")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${s.title} — إرث\nhttps://medmrf-10.github.io/irth/#/series/$seriesId")
                        }
                        ctx.startActivity(Intent.createChooser(send, "مشاركة السلسلة"))
                    },
                    modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.muted),
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.soft),
                    contentPadding = PaddingValues(0.dp),
                ) { Icon(Icons.Filled.Share, "مشاركة السلسلة", tint = p.muted, modifier = Modifier.size(18.dp)) }
            }
        }
        itemsIndexed(s.items) { i, vid ->
            val v = Repo.video(vid) ?: return@itemsIndexed
            val pv = Prefs.progress(vid)
            val fin = Prefs.finished(vid, v.duration)
            val ong = Prefs.ongoing(vid, v.duration)
            val meta = buildString {
                append(fmtDur(v.duration))
                if (ong) append(" · متبقٍ ").append(fmtDur((v.duration - pv).coerceAtLeast(0)))
                if (Prefs.isFav(vid)) append(" · ♥")
            }
            RowCard(
                p, i, v.title,
                meta = meta,
                dim = fin,
                bar = if (ong && !fin) progressBar(vid, v.duration) else 0f,
                leading = { PhIcon(p, "${i + 1}", done = fin) },
                trailing = {
                    IconButton(onClick = { Prefs.toggleFav(vid) }) {
                        Icon(
                            if (Prefs.isFav(vid)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            null, tint = if (Prefs.isFav(vid)) p.gold else p.muted,
                        )
                    }
                },
                onClick = { onLesson(vid) },
            )
        }
    }
}

@Composable
fun FavScreen(p: Pal, onLesson: (String) -> Unit) {
    val favs = Prefs.favs.value.mapNotNull { Repo.video(it) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("المفضلة", style = MaterialTheme.typography.displayLarge, color = p.text)
            }
        }
        if (favs.isEmpty()) item { EmptyBox(p, "لا مفضلات بعد — أضف دروساً بالقلب") }
        itemsIndexed(favs) { i, v ->
            RowCard(
                p, i, v.title, meta = fmtDur(v.duration),
                dim = Prefs.finished(v.id, v.duration),
                bar = progressBar(v.id, v.duration).takeIf { Prefs.ongoing(v.id, v.duration) } ?: 0f,
                leading = { PhIcon(p, v.title.first().toString(), done = Prefs.finished(v.id, v.duration)) },
                trailing = {
                    IconButton(onClick = { Prefs.toggleFav(v.id) }) {
                        Icon(Icons.Filled.Favorite, null, tint = p.gold)
                    }
                },
                onClick = { onLesson(v.id) },
            )
        }
    }
}

@Composable
fun StatsScreen(p: Pal) {
    val vids = Repo.videos ?: emptyList()
    val opened = Prefs.openedCount()
    val fin = vids.count { Prefs.finished(it.id, it.duration) }
    val ong = vids.count { Prefs.ongoing(it.id, it.duration) }
    val fav = Prefs.favs.value.size
    val total = vids.size
    val totalDur = vids.sumOf { it.duration }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("إحصائي", style = MaterialTheme.typography.displayLarge, color = p.text)
            }
        }
        item {
            StatRow(p, "الدروس الكلية", "$total")
            StatRow(p, "مدة المكتبة", fmtDur(totalDur))
            StatRow(p, "السلاسل", "${Repo.series?.size ?: 0}")
            StatRow(p, "دروس فتحتها", "$opened", if (total > 0) opened.toFloat() / total else 0f)
            StatRow(p, "قيد المشاهدة", "$ong", if (total > 0) ong.toFloat() / total else 0f)
            StatRow(p, "دروس أتممتها", "$fin", if (total > 0) fin.toFloat() / total else 0f)
            StatRow(p, "مفضلاتك", "$fav")
        }
    }
}

@Composable
private fun StatRow(p: Pal, k: String, v: String, frac: Float = -1f) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(k, color = p.muted, style = MaterialTheme.typography.bodyLarge)
            Text(v, color = p.gold, style = MaterialTheme.typography.titleMedium)
        }
        if (frac >= 0f) {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(4.dp)).background(p.soft)) {
                Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(p.gold))
            }
        }
    }
}

@Composable
fun SearchSheet(p: Pal, onLesson: (String) -> Unit, onSeries: (String) -> Unit, onScience: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val t = q.trim()
    val vids = remember(t) { Repo.search(t).take(30) }
    val sers = remember(t) { if (t.isEmpty()) emptyList() else Repo.series?.filter { it.title.contains(t) }?.take(10) ?: emptyList() }
    val scis = remember(t) { if (t.isEmpty()) emptyList() else Repo.sciences?.filter { it.name.contains(t) } ?: emptyList() }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ابحث: درساً أو سلسلة أو علماً…", color = p.muted) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = p.muted) },
            trailingIcon = {
                if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, null, tint = p.muted) }
            },
            singleLine = true,
            shape = RoundedCornerShape(999.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = p.gold, unfocusedBorderColor = p.line,
                cursorColor = p.gold, focusedTextColor = p.text, unfocusedTextColor = p.text,
            ),
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            if (t.isNotEmpty() && vids.isEmpty() && sers.isEmpty() && scis.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.SearchOff, null, tint = p.muted, modifier = Modifier.size(34.dp))
                        Text("لا نتائج لـ«$t»", color = p.muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (scis.isNotEmpty()) {
                item { SectionTitle(p, "العلوم") }
                itemsIndexed(scis) { _, s ->
                    ListItem(
                        headlineContent = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Filled.MenuBook, null, tint = p.gold) },
                        modifier = Modifier.clickable { onScience(s.id) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
            if (sers.isNotEmpty()) {
                item { SectionTitle(p, "السلاسل") }
                itemsIndexed(sers) { _, s ->
                    ListItem(
                        headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(pl3(s.items.size, "درس", "درسان", "دروس", "درساً"), color = p.gold) },
                        leadingContent = { Icon(Icons.Filled.FormatListBulleted, null, tint = p.gold) },
                        modifier = Modifier.clickable { onSeries(s.id) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
            if (vids.isNotEmpty()) {
                item { SectionTitle(p, "الدروس") }
                itemsIndexed(vids) { _, v ->
                    ListItem(
                        headlineContent = { Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(fmtDur(v.duration), color = p.gold) },
                        leadingContent = { Icon(Icons.Filled.PlayArrow, null, tint = p.muted) },
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .clickable { onLesson(v.id) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                    HorizontalDivider(color = p.line)
                }
            }
        }
    }
}
