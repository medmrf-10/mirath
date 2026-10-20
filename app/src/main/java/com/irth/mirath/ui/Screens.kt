package com.irth.mirath.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irth.mirath.data.Haw
import com.irth.mirath.data.LocalLib
import com.irth.mirath.data.QA
import com.irth.mirath.data.Repo
import com.irth.mirath.data.TextIndex
import com.irth.mirath.store.Prefs
import com.irth.mirath.ui.theme.Pal
import kotlinx.coroutines.launch

private fun progressBar(id: String, duration: Int): Float {
    val pv = Prefs.progress(id)
    return if (pv > 10 && duration > 0) (pv.toFloat() / duration).coerceAtMost(0.98f) else 0f
}

/** أطول بادئة كلمات مشتركة بين عناوين دروس سلسلة (كلمتان فأكثر) — لحذف تكرارها من كل صف. */
private fun commonTitlePrefix(titles: List<String>): String {
    if (titles.size < 3) return ""
    val split = titles.map { it.trim().split(Regex("\\s+")) }
    val n = split.minOf { it.size } - 1
    var k = 0
    while (k < n && split.all { it[k] == split[0][k] }) k++
    return if (k >= 2) split[0].take(k).joinToString(" ") else ""
}

private fun shortTitle(title: String, prefix: String): String {
    if (prefix.isEmpty() || !title.startsWith(prefix)) return title
    val rem = title.removePrefix(prefix).trim().trimStart('-', '–', '—', ':', '،', ' ').trim()
    return rem.ifEmpty { title }
}

@Composable
fun HomeScreen(p: Pal, onScience: (String) -> Unit, onLesson: (String) -> Unit, onSearch: () -> Unit = {}) {
    val srcRev = Prefs.srcRev.value
    val sciences = Repo.sciences
    if (sciences == null) { LoadingBox(p); return }
    val lastId = Prefs.last()
    val lastV = lastId?.let { Repo.video(it) }
    val showLast = lastV != null && Prefs.ongoing(lastV.id, lastV.duration)
    val recent = Prefs.recent.value.mapNotNull { Repo.video(it) }
        .filter { Prefs.progress(it.id) > 10 && it.id != lastV?.id }.take(8)
    val totalSeries = Repo.series?.size ?: 0
    val totalLessons = Repo.videos?.size ?: 0
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PageHeader(p, "العلماء والمشايخ", "${sciences.size} شيخاً · $totalSeries سلسلة · $totalLessons درساً")
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.soft)
                    .clickable(onClick = onSearch).padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = p.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("ابحث عن درس أو سلسلة أو شيخ، أو عبارة سمعتها…", color = p.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (showLast && lastV != null) item(span = { GridItemSpan(maxLineSpan) }) {
            val left = (lastV.duration - Prefs.progress(lastV.id)).coerceAtLeast(0)
            Row(
                Modifier.fillMaxWidth().appCard(p, { onLesson(lastV.id) }, highlight = true).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(p, lastV.yt, Modifier.width(112.dp).aspectRatio(16f / 9f), label = if (lastV.yt.isEmpty()) "${lastV.title.firstOrNull() ?: ' '}" else "")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("أكمل المشاهدة", color = p.gold, style = MaterialTheme.typography.labelMedium)
                    Text(lastV.title, color = p.text, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    ThinBar(p, progressBar(lastV.id, lastV.duration))
                    if (left > 0) Text("متبقٍ ${fmtDur(left)}", color = p.muted, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.gold), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, "تشغيل", tint = p.bg, modifier = Modifier.size(26.dp))
                }
            }
        }
        if (recent.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("آخر ما شغّلت", color = p.gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recent, key = { it.id }) { v ->
                        Column(Modifier.width(150.dp).appCard(p, { onLesson(v.id) }).padding(8.dp)) {
                            Thumb(p, v.yt, Modifier.fillMaxWidth().aspectRatio(16f / 9f), label = if (v.yt.isEmpty()) "${v.title.firstOrNull() ?: ' '}" else "")
                            Text(
                                v.title, color = p.text, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp).height(34.dp),
                            )
                            ThinBar(p, progressBar(v.id, v.duration), Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("المشايخ", color = p.gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
        }
        items(sciences, key = { it.id }) { s ->
            val scSeries = Repo.seriesOfScience(s.id)
            val sCount = if (s.seriesCount > 0) s.seriesCount else scSeries.size
            val lCount = if (s.lessonsCount > 0) s.lessonsCount else scSeries.sumOf { it.items.size }
            val aCount = if (s.alignedCount > 0) s.alignedCount else scSeries.sumOf { it.alignedCount }
            Column(
                Modifier.fillMaxWidth().appCard(p, { onScience(s.id) }).padding(horizontal = 10.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ph = Haw.photoOf(s.id)
                if (ph != null) coil.compose.AsyncImage(
                    model = ph, contentDescription = s.name,
                    modifier = Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.soft),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                ) else PhIcon(p, s.name.firstOrNull()?.toString() ?: "م", size = 72)
                Spacer(Modifier.height(10.dp))
                Text(
                    s.name, color = p.text, fontFamily = com.irth.mirath.ui.theme.Amiri, fontWeight = FontWeight.Bold,
                    fontSize = 17.sp, lineHeight = 25.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.height(50.dp),
                )
                Text(
                    listOf(if (sCount > 0) "$sCount سلسلة" else "", if (lCount > 0) "$lCount درساً" else "").filter { it.isNotEmpty() }.joinToString(" · "),
                    color = p.muted, fontSize = 12.sp, maxLines = 1,
                )
                if (aCount > 0) Text(
                    "حيّ $aCount", color = p.gold, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp).border(1.dp, p.gold, RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun ScienceScreen(p: Pal, scienceId: String, onSeries: (String) -> Unit, onLesson: (String) -> Unit) {
    val srcRev = Prefs.srcRev.value
    val science = Repo.sciences?.firstOrNull { it.id == scienceId }
    val allSeries = Repo.seriesOfScience(scienceId)
    val works = Repo.worksOfScience(scienceId)
    val categories = remember(allSeries) {
        allSeries.map { it.kind.trim() }.filter { it.isNotEmpty() }.distinct()
    }
    var selectedCat by remember(scienceId) { mutableStateOf("") }
    var onlyAligned by remember(scienceId) { mutableStateOf(false) }
    val series = remember(allSeries, selectedCat, onlyAligned) {
        allSeries.filter { s ->
            (selectedCat.isEmpty() || s.kind.trim() == selectedCat) &&
                (!onlyAligned || s.alignedCount > 0)
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                val ph = Haw.photoOf(scienceId)
                if (ph != null) {
                    coil.compose.AsyncImage(
                        model = ph, contentDescription = science?.name,
                        modifier = Modifier.size(84.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.soft),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(science?.name ?: "", fontFamily = com.irth.mirath.ui.theme.Amiri, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 38.sp, color = p.text)
                    val totalL = allSeries.sumOf { if (it.lessonsCount > 0) it.lessonsCount else it.items.size }
                    val totalA = allSeries.sumOf { it.alignedCount }
                    Text(
                        "${allSeries.size} سلسلة · $totalL درساً" + if (totalA > 0) " · $totalA محاذى حياً" else "",
                        color = p.muted, fontSize = 13.sp,
                    )
                }
            }
        }
        if (categories.size > 1 || allSeries.any { it.alignedCount > 0 }) {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChipItem(p, "الكل (${allSeries.size})", selectedCat.isEmpty() && !onlyAligned) {
                        selectedCat = ""; onlyAligned = false
                    }
                    if (allSeries.any { it.alignedCount > 0 }) {
                        FilterChipItem(p, "محاذى حياً", onlyAligned) {
                            onlyAligned = !onlyAligned
                        }
                    }
                    categories.forEach { cat ->
                        FilterChipItem(p, cat, selectedCat == cat) {
                            selectedCat = if (selectedCat == cat) "" else cat
                        }
                    }
                }
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
            val n = if (s.items.isNotEmpty()) s.items.size else s.lessonsCount
            val nw = s.items.count { Prefs.progress(it) <= 10 }
            val finN = s.items.count { vid -> Repo.video(vid)?.let { Prefs.finished(vid, it.duration) } == true }
            val wp = if (n > 0) finN * 100 / n else 0
            val tag = when {
                wp == 100 -> "اكتملت ✓"
                s.alignedCount > 0 -> "حيّ ${s.alignedCount}/$n"
                nw in 1 until n -> pl3(nw, "درس جديد", "درسان جديدان", "دروس جديدة", "درساً جديداً")
                else -> ""
            }
            val durStr = if (s.duration > 0) fmtDur(s.duration) else ""
            RowCard(
                p, i, s.title,
                meta = listOf(pl3(n, "درس", "درسان", "دروس", "درساً"), durStr, s.kind).filter { it.isNotEmpty() }.joinToString(" · "),
                tag = tag,
                bar = if (wp in 1..99) wp / 100f else 0f,
                leading = { PhIcon(p, "${i + 1}", done = wp == 100) },
                onClick = { onSeries(s.id) },
            )
        }
        if (series.isEmpty()) item { EmptyBox(p, "لا سلاسل تطابق الفلتر المختار") }
    }
}

@Composable
private fun FilterChipItem(p: Pal, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) p.soft else p.card)
            .border(1.dp, if (selected) p.gold else p.line, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) p.gold else p.muted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun SeriesScreen(p: Pal, seriesId: String, onLesson: (String) -> Unit, onExam: (String) -> Unit = {}) {
    val srcRev = Prefs.srcRev.value
    LaunchedEffect(seriesId) { runCatching { Haw.ensure(seriesId) } }
    val s = Repo.seriesOf(seriesId)
    if (s == null) { EmptyBox(p, "السلسلة غير موجودة"); return }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var linkMsg by remember { mutableStateOf<String?>(null) }
    var linkBusy by remember { mutableStateOf(false) }
    val hasMedia = remember(seriesId, Prefs.srcRev.value) { LocalLib.seriesHasMedia(seriesId, s.items) }
    var linkPlan by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var linkMedia by remember { mutableStateOf<List<Pair<String, Uri>>>(emptyList()) }
    var dlBusy by remember { mutableStateOf(false) }
    var dlMsg by remember { mutableStateOf<String?>(null) }
    val pickLink = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        u?.let {
            scope.launch {
                linkBusy = true
                val found = LocalLib.scanMediaTree(ctx, it)
                linkBusy = false
                when {
                    found.isEmpty() -> linkMsg = "لا ملفات وسائط في المجلد المختار"
                    found.size != s.items.size -> linkMsg = "عدد ملفات المجلد (${found.size}) لا يساوي عدد الدروس (${s.items.size}) — رتّب مجلدك وحدثه"
                    else -> { linkMedia = found; linkPlan = s.items.mapIndexed { i, vid -> vid to found[i].first } }
                }
            }
        }
    }
    var attachFor by remember { mutableStateOf<String?>(null) }
    val attachPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        val tgt = attachFor
        if (u != null && tgt != null) scope.launch {
            val ok = if (seriesId.startsWith("L:")) {
                LocalLib.attachTo(ctx, seriesId, tgt.removePrefix("L:").substringAfterLast("/"), u)
            } else LocalLib.attachToCatalog(ctx, tgt, u)
            if (ok) { Repo.mergeLocal(); Prefs.srcRev.value++; linkMsg = "أُلحق الملف بالدرس" }
            else linkMsg = "تعذّر إرفاق الملف"
        }
    }
    val cont = s.items.firstOrNull { vid -> Repo.video(vid)?.let { Prefs.ongoing(vid, it.duration) } == true }
    val finN = s.items.count { vid -> Repo.video(vid)?.let { Prefs.finished(vid, it.duration) } == true }
    val common = remember(seriesId, srcRev) { commonTitlePrefix(s.items.mapNotNull { Repo.video(it)?.title }) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            val alCount = s.items.count { Repo.video(it)?.has?.contains("align") == true }
            PageHeader(
                p, s.title,
                pl3(s.items.size, "درس", "درسان", "دروس", "درساً") + if (alCount > 0) " · $alCount محاذى حياً" else "",
            )
        }
        if (finN > 0 && s.items.isNotEmpty()) {
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
                Button(
                    onClick = { s.items.firstOrNull()?.let { firstId -> onLesson(cont ?: firstId) } },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.gold, contentColor = p.bg),
                    enabled = s.items.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (cont != null) "أكمل من حيث توقفت" else "استمع من البداية", fontWeight = FontWeight.Bold)
                }
                if (hasMedia) {
                    Spacer(Modifier.width(8.dp))
                    val local = Prefs.src(seriesId) == "local"
                    OutlinedButton(
                        onClick = { Prefs.setSrc(seriesId, if (local) "yt" else "local") },
                        modifier = Modifier.height(44.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (local) p.gold else p.muted),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (local) p.gold else p.soft),
                    ) { Text(if (local) "محلي" else "يوتيوب", fontSize = 13.sp) }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { pickLink.launch(null) },
                    modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (hasMedia) p.gold else p.muted),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (hasMedia) p.gold else p.soft),
                    enabled = !linkBusy,
                    contentPadding = PaddingValues(0.dp),
                ) { Icon(Icons.Filled.Link, "اربط مجلد أوفلاين", tint = if (hasMedia) p.gold else p.muted, modifier = Modifier.size(18.dp)) }
                if (seriesId.startsWith("H:")) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            dlBusy = true; dlMsg = null
                            Haw.bg.launch {
                                var done = 0
                                for (vid in s.items) {
                                    runCatching { Repo.transcript(vid); Repo.summary(vid); Repo.align(vid) }
                                    done++; dlMsg = "نزّل $done/${s.items.size}"
                                }
                                dlBusy = false
                                dlMsg = "متاحة بلا نت (${s.items.size} درساً)"
                            }
                        },
                        modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = p.muted),
                        border = androidx.compose.foundation.BorderStroke(1.dp, p.soft),
                        enabled = !dlBusy,
                        contentPadding = PaddingValues(0.dp),
                    ) { Icon(Icons.Filled.Download, "نزّل طبقات السلسلة للأوفلاين", tint = p.muted, modifier = Modifier.size(18.dp)) }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            val firstYt = s.items.firstOrNull()?.let { Repo.video(it)?.yt }.orEmpty()
                            val shareUrl = if (firstYt.isNotEmpty()) "https://youtu.be/$firstYt" else "https://github.com/medmrf-10/ytb"
                            putExtra(Intent.EXTRA_TEXT, "${s.title} — ميراث\n$shareUrl")
                        }
                        ctx.startActivity(Intent.createChooser(send, "مشاركة السلسلة"))
                    },
                    modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.muted),
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.soft),
                    contentPadding = PaddingValues(0.dp),
                ) { Icon(Icons.Filled.Share, "مشاركة السلسلة", tint = p.muted, modifier = Modifier.size(18.dp)) }
            }
            if (linkBusy || dlBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp), color = p.gold, trackColor = p.soft)
            linkMsg?.let { Text(it, color = p.gold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            dlMsg?.let { Text(it, color = p.gold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            if (s.items.any { Repo.video(it)?.has?.contains("questions") == true }) {
                OutlinedButton(
                    onClick = { onExam(seriesId) },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
                ) {
                    Icon(Icons.Outlined.Quiz, null, tint = p.gold, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("اختبار السلسلة — أسئلة من كل دروسها", fontSize = 13.sp)
                }
            }
        }
        itemsIndexed(s.items) { i, vid ->
            val v = Repo.video(vid) ?: return@itemsIndexed
            val pv = Prefs.progress(vid)
            val fin = Prefs.finished(vid, v.duration)
            val ong = Prefs.ongoing(vid, v.duration)
            val hasAlign = v.has.contains("align")
            val meta = buildString {
                if (v.duration > 0) append(fmtDur(v.duration))
                if (ong) {
                    if (isNotEmpty()) append(" · ")
                    append("متبقٍ ").append(fmtDur((v.duration - pv).coerceAtLeast(0)))
                }
                if (Prefs.isFav(vid)) append(" · ♥")
            }
            RowCard(
                p, i, shortTitle(v.title, common),
                meta = meta,
                tag = if (hasAlign) "حيّ" else "",
                dim = fin,
                bar = if (ong && !fin) progressBar(vid, v.duration) else 0f,
                leading = { PhIcon(p, "${i + 1}", done = fin) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { attachFor = vid; attachPick.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Filled.AttachFile, "أرفق ملفاً بهذا الدرس", tint = p.muted, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { Prefs.toggleFav(vid) }) {
                            Icon(
                                if (Prefs.isFav(vid)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                null, tint = if (Prefs.isFav(vid)) p.gold else p.muted,
                            )
                        }
                    }
                },
                onClick = { onLesson(vid) },
            )
        }
    }

    linkPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { linkPlan = null },
            containerColor = p.card,
            title = { Text("راجِع الربط — ${plan.size} درساً ← ${plan.size} ملفاً", color = p.text, fontSize = 15.sp) },
            text = {
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    itemsIndexed(plan) { i, pair ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text("${i + 1}.", color = p.gold, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text(Repo.video(pair.first)?.title ?: pair.first, color = p.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("← ${pair.second}", color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val m = linkMedia
                    scope.launch {
                        linkBusy = true
                        val n = LocalLib.applyLink(ctx, s.items, m)
                        linkBusy = false
                        linkPlan = null
                        if (n > 0) { Prefs.setSrc(seriesId, "local"); linkMsg = "رُبط $n ملف — «محلي» مفعّل بلا نت" }
                        else linkMsg = "تعذّر الربط"
                    }
                }) { Text("ثبّت الربط", color = p.gold) }
            },
            dismissButton = { TextButton(onClick = { linkPlan = null }) { Text("إلغاء", color = p.muted) } },
        )
    }
}

@Composable
fun FavScreen(p: Pal, onLesson: (String) -> Unit, onBrowse: () -> Unit = {}) {
    val favs = Prefs.favs.value.mapNotNull { Repo.video(it) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageHeader(p, "المفضلة", if (favs.isEmpty()) "" else pl3(favs.size, "درس واحد", "درسان", "دروس", "درساً") + " محفوظة للوصول السريع")
        }
        if (favs.isEmpty()) item {
            EmptyState(
                p, Icons.Filled.FavoriteBorder, "لا مفضلات بعد",
                "اضغط القلب بجانب أي درس ليظهر هنا للوصول السريع", "تصفّح المكتبة", onBrowse,
            )
        }
        itemsIndexed(favs, key = { _, v -> v.id }) { i, v ->
            RowCard(
                p, i, v.title, meta = fmtDur(v.duration),
                tag = if (v.has.contains("align")) "حيّ" else "",
                dim = Prefs.finished(v.id, v.duration),
                bar = progressBar(v.id, v.duration).takeIf { Prefs.ongoing(v.id, v.duration) } ?: 0f,
                leading = { Thumb(p, v.yt, Modifier.width(88.dp).aspectRatio(16f / 9f), label = if (v.yt.isEmpty()) "${v.title.firstOrNull() ?: ' '}" else "") },
                trailing = {
                    IconButton(onClick = { Prefs.toggleFav(v.id) }) {
                        Icon(Icons.Filled.Favorite, "إزالة من المفضلة", tint = p.gold)
                    }
                },
                onClick = { onLesson(v.id) },
            )
        }
        Prefs.bmRev.value
        val bms = Prefs.allBookmarks()
        if (bms.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
                    Text("علاماتي", style = MaterialTheme.typography.titleLarge, color = p.text)
                }
            }
            bms.entries.forEach { (vid, bl) ->
                val bv = Repo.video(vid) ?: return@forEach
                itemsIndexed(bl.take(5)) { _, b ->
                    RowCard(
                        p, b.s, bv.title,
                        subtitle = if (b.n.isNotEmpty()) b.n else "علامة",
                        meta = fmtDur(b.s),
                        leading = { PhIcon(p, "•", done = false) },
                        onClick = { onLesson(vid) },
                    )
                }
            }
        }
    }
}

@Composable
fun StatsScreen(p: Pal, onLesson: (String) -> Unit = {}) {
    val vids = Repo.videos ?: emptyList()
    val opened = Prefs.openedCount()
    val fin = vids.count { Prefs.finished(it.id, it.duration) }
    val ong = vids.count { Prefs.ongoing(it.id, it.duration) }
    val fav = Prefs.favs.value.size
    val total = vids.size
    val aligned = vids.count { it.has.contains("align") }
    val totalDur = vids.sumOf { it.duration }
    val pct = if (total > 0) fin * 100 / total else 0
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader(p, "إحصائي", "تقدمك ومكتبتك في لمحة") }
        item {
            Column(Modifier.fillMaxWidth().appCard(p, highlight = fin > 0).padding(18.dp)) {
                Text("تقدمك في المكتبة", color = p.gold, style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$pct%", color = p.text, fontFamily = com.irth.mirath.ui.theme.Amiri, fontWeight = FontWeight.Bold, fontSize = 44.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("أتممت $fin من $total درساً", color = p.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
                }
                ThinBar(p, if (total > 0) fin.toFloat() / total else 0f)
            }
        }
        item { Spacer(Modifier.height(12.dp)); StatPair(p, StatTileData("قيد المشاهدة", "$ong", Icons.Filled.PlayCircle), StatTileData("أتممتها", "$fin", Icons.Filled.CheckCircle)) }
        item { Spacer(Modifier.height(12.dp)); StatPair(p, StatTileData("فتحتها", "$opened", Icons.Filled.Visibility), StatTileData("مفضلاتك", "$fav", Icons.Filled.Favorite)) }
        item { Text("المكتبة", color = p.gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)) }
        item { StatPair(p, StatTileData("المشايخ", "${Repo.sciences?.size ?: 0}", Icons.Filled.Groups), StatTileData("السلاسل", "${Repo.series?.size ?: 0}", Icons.Filled.FormatListBulleted)) }
        item {
            Spacer(Modifier.height(12.dp))
            StatPair(
                p, StatTileData("الدروس", "$total", Icons.Filled.MenuBook),
                StatTileData("مدة المكتبة", if (totalDur > 0) fmtDur(totalDur) else "—", Icons.Filled.Schedule),
            )
        }
        item {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().appCard(p).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("دروس المحاذاة الحيّة", color = p.muted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("$aligned", color = p.gold, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(8.dp))
                ThinBar(p, if (total > 0) aligned.toFloat() / total else 0f)
            }
        }
        val bms = Prefs.allBookmarks().entries
            .flatMap { (vid, l) -> l.map { vid to it } }
            .sortedByDescending { it.second.t }
        if (bms.isNotEmpty()) {
            item { Text("علاماتك (${bms.size})", color = p.gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)) }
            items(bms.size) { idx ->
                val (vid, b) = bms[idx]
                val vv = Repo.video(vid)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).appCard(p, { onLesson(vid) }, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(fmtDur(b.s), color = p.gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(vv?.title ?: vid, color = p.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (b.n.isNotEmpty()) Text(b.n, color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private data class StatTileData(val label: String, val value: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun StatPair(p: Pal, a: StatTileData, b: StatTileData) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(p, a, Modifier.weight(1f))
        StatTile(p, b, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(p: Pal, d: StatTileData, modifier: Modifier) {
    Column(modifier.appCard(p).padding(16.dp)) {
        Icon(d.icon, null, tint = p.gold, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Text(d.value, color = p.text, fontFamily = com.irth.mirath.ui.theme.Amiri, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp)
        Text(d.label, color = p.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SearchSheet(
    p: Pal,
    onLesson: (String) -> Unit,
    onSeries: (String) -> Unit,
    onScience: (String) -> Unit,
    onHit: (String, Int) -> Unit = { id, _ -> onLesson(id) },
) {
    var q by remember { mutableStateOf("") }
    var inText by remember { mutableStateOf(Prefs.str("search.scope") == "text") }
    val t = q.trim()
    val ft = TextIndex.fold(t)
    val vids = remember(t) { Repo.search(t).take(30) }
    val sers = remember(ft) { if (ft.isEmpty()) emptyList() else Repo.series?.filter { TextIndex.fold(it.title).contains(ft) }?.take(12) ?: emptyList() }
    val scis = remember(ft) { if (ft.isEmpty()) emptyList() else Repo.sciences?.filter { TextIndex.fold(it.name).contains(ft) } ?: emptyList() }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(if (inText) "عبارة سمعتها في درس…" else "ابحث: درساً أو سلسلة أو شيخاً…", color = p.muted) },
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
        Spacer(Modifier.height(8.dp))
        // نطاق البحث: العناوين | داخل نصوص الدروس
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(p.bg).border(1.dp, p.line, RoundedCornerShape(999.dp)).padding(3.dp),
        ) {
            listOf(false to "العناوين", true to "داخل الدروس").forEach { (txt, label) ->
                val on = inText == txt
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(999.dp))
                        .background(if (on) p.soft else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { inText = txt; Prefs.putStr("search.scope", if (txt) "text" else "") },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (on) p.primary else p.muted, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (inText) {
            TextSearchPane(p, q, onQuery = { q = it }, onHit = onHit)
            return@Column
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            if (t.length >= 2) item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { inText = true; Prefs.putStr("search.scope", "text") }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = p.gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ابحث عن «$t» داخل نصوص الدروس", color = p.gold, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("←", color = p.gold)
                }
            }
            if (t.isNotEmpty() && vids.isEmpty() && sers.isEmpty() && scis.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.SearchOff, null, tint = p.muted, modifier = Modifier.size(34.dp))
                        Text("لا عناوين تطابق «$t»", color = p.muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (scis.isNotEmpty()) {
                item { SectionTitle(p, "المشايخ") }
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
                        supportingContent = {
                            val sub = buildString {
                                if (v.duration > 0) append(fmtDur(v.duration))
                                if (v.has.contains("align")) {
                                    if (isNotEmpty()) append(" · ")
                                    append("محاذاة حية")
                                }
                            }
                            Text(sub, color = p.gold)
                        },
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

/** اختبار السلسلة: يجمع أسئلة كل دروسها، يسحب حتى 15 سؤالاً عشوائياً، ويشغّل وضع الاختبار نفسه. */
@Composable
fun ExamScreen(p: Pal, seriesId: String, onBack: () -> Unit) {
    val s = Repo.seriesOf(seriesId)
    var qs by remember { mutableStateOf<List<QA>?>(null) }
    var loadN by remember { mutableStateOf(0) }
    LaunchedEffect(seriesId) {
        if (s == null) { qs = emptyList(); return@LaunchedEffect }
        val pool = mutableListOf<QA>()
        s.items.forEach { vid ->
            if (Repo.video(vid)?.has?.contains("questions") == true) {
                runCatching { pool += Repo.questions(vid) }
            }
            loadN++
        }
        qs = pool.shuffled().take(15)
    }
    when {
        s == null -> EmptyBox(p, "السلسلة غير موجودة")
        qs == null -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            CircularProgressIndicator(color = p.gold)
            Text("يجمع أسئلة السلسلة… ($loadN/${s.items.size})", color = p.muted, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.weight(1f))
        }
        qs!!.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp)) {
            EmptyBox(p, "لا أسئلة في دروس هذه السلسلة بعد")
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = p.primary)) { Text("رجوع") }
        }
        else -> QuizMode(p, "EXAM:$seriesId", qs!!, onBack)
    }
}
