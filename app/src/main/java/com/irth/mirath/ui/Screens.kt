package com.irth.mirath.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.irth.mirath.data.Api
import com.irth.mirath.data.Repo
import com.irth.mirath.data.Video
import com.irth.mirath.store.Prefs
import com.irth.mirath.ui.theme.Pal

@Composable
fun HomeScreen(p: Pal, onScience: (String) -> Unit) {
    val sciences = Repo.sciences
    if (sciences == null) { LoadingBox(p); return }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("العلوم", style = MaterialTheme.typography.displayLarge, color = p.text)
                Text("اختر علماً لتصف سلاسله", color = p.muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
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
            RowCard(
                p, i, s.title,
                meta = "$n ${if (n in 3..10) "دروس" else "درساً"}",
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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.title, style = MaterialTheme.typography.displayLarge, color = p.text)
                Text("${s.items.size} درساً", color = p.gold, style = MaterialTheme.typography.labelMedium)
            }
        }
        itemsIndexed(s.items) { i, vid ->
            val v = Repo.video(vid) ?: return@itemsIndexed
            val prg = Prefs.progress(vid)
            val meta = buildString {
                append(fmtDur(v.duration))
                if (v.speakers.isNotEmpty()) append(" · ").append(v.speakers.joinToString("، ") { Repo.personName(it) })
                if (prg > 0) append(" · بقي ").append(fmtDur((v.duration - prg).coerceAtLeast(0)))
            }
            RowCard(
                p, i, v.title,
                meta = meta,
                leading = { PhIcon(p, "${i + 1}") },
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
                leading = { PhIcon(p, v.title.first().toString()) },
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
            StatRow(p, "دروس فتحتها", "$opened")
            StatRow(p, "مفضلاتك", "$fav")
        }
    }
}

@Composable
private fun StatRow(p: Pal, k: String, v: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(k, color = p.muted, style = MaterialTheme.typography.bodyLarge)
        Text(v, color = p.gold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SearchSheet(p: Pal, onLesson: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val results = remember(q) { Repo.search(q) }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ابحث في الدروس…", color = p.muted) },
            singleLine = true,
            shape = RoundedCornerShape(999.dp),
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            itemsIndexed(results.take(40)) { _, v ->
                ListItem(
                    headlineContent = { Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(fmtDur(v.duration), color = p.gold) },
                    leadingContent = { Icon(Icons.Filled.PlayArrow, null, tint = p.muted) },
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .clickable { onLesson(v.id) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
                Divider(color = p.line)
            }
        }
    }
}
