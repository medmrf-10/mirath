package com.irth.mirath.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.alpha
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
import com.irth.mirath.store.Prefs
import com.irth.mirath.ui.theme.Pal
import kotlinx.coroutines.launch

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
            RowCard(p, i, s.name, onClick = { onScience(s.id) }, leading = {
                val ph = Haw.photoOf(s.id)
                if (ph != null) coil.compose.AsyncImage(model = ph, contentDescription = s.name,
                    modifier = Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                else PhIcon(p, s.name.first().toString())
            })
        }
    }
}

@Composable
fun ScienceScreen(p: Pal, scienceId: String, onSeries: (String) -> Unit, onLesson: (String) -> Unit) {
    val science = Repo.sciences?.firstOrNull { it.id == scienceId }
    val series = Repo.seriesOfScience(scienceId)
    val works = Repo.worksOfScience(scienceId)
    var dlMsg by remember { mutableStateOf<String?>(null) }
    var dlBusy by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Haw.photoOf(scienceId)?.let { ph ->
                    coil.compose.AsyncImage(model = ph, contentDescription = science?.name,
                        modifier = Modifier.padding(bottom = 8.dp).size(88.dp).clip(androidx.compose.foundation.shape.CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
                Text(science?.name ?: "", style = MaterialTheme.typography.displayLarge, color = p.text)
                if (scienceId.startsWith("H:")) {
                    TextButton(
                        onClick = {
                            dlBusy = true
                            Haw.bg.launch {
                                var done = 0; var tot = 0
                                for (sr in series) {
                                    runCatching { Haw.ensure(sr.id) }
                                    val items = Repo.seriesOf(sr.id)?.items ?: continue
                                    tot += items.size
                                    for (vid in items) {
                                        runCatching { Repo.transcript(vid); Repo.summary(vid); Repo.align(vid) }
                                        done++; dlMsg = "نزّل $done درساً…"
                                    }
                                }
                                dlBusy = false
                                dlMsg = "كل طبقات الشيخ بلا نت ($tot درساً)"
                            }
                        },
                        enabled = !dlBusy,
                    ) { Text(dlMsg ?: "نزّل كل الطبقات", color = if (dlBusy) p.muted else p.gold, fontSize = 13.sp) }
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
fun SeriesScreen(p: Pal, seriesId: String, onLesson: (String) -> Unit, onExam: (String) -> Unit = {}) {
    val srcRev = Prefs.srcRev.value  // إعادة تركيب بعد تحميل دروس هوشات كسولاً
    LaunchedEffect(seriesId) { runCatching { Haw.ensure(seriesId) } }
    val s = Repo.seriesOf(seriesId)
    if (s == null) { EmptyBox(p, "السلسلة غير موجودة"); return }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var linkMsg by remember { mutableStateOf<String?>(null) }
    var linkBusy by remember { mutableStateOf(false) }
    val hasMedia = remember(seriesId, Prefs.srcRev.value) { LocalLib.seriesHasMedia(seriesId, s.items) }
    // مراجعة الربط قبل التثبيت: نعرض «درس ← ملف» كاملاً فلا يُربط أعمى (بأمر محمد)
    var linkPlan by remember { mutableStateOf<List<Pair<String, String>>?>(null) }  // (vid, اسم الملف)
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
    // إرفاق ملف بدرس محدد — سهل وبسيط كما أمر محمد: أيقونة مشبك على كل درس
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
                            val shareUrl = if (seriesId.startsWith("H:")) "https://medmrf-10.github.io/hawshat/playlists/d.html?s=${seriesId.removePrefix("H:")}" else "https://medmrf-10.github.io/irth/#/series/$seriesId"
                            putExtra(Intent.EXTRA_TEXT, "${s.title} — إرث\n$shareUrl")
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
            // اختبار السلسلة — يجمع أسئلة كل دروسها في امتحان واحد
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

    // شاشة مراجعة الربط: كل درس ← ملفه قبل أي نسخ — لا ربط أعمى
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
        Prefs.bmRev.value // اشتراك إعادة التركيب للعلامات
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
            val bms = remember(Prefs.bmRev.value) {
                Prefs.allBookmarks().entries
                    .flatMap { (vid, l) -> l.map { vid to it } }
                    .sortedByDescending { it.second.t }
            }
            if (bms.isNotEmpty()) {
                StatRow(p, "علاماتك", "${bms.size}")
                bms.forEach { (vid, b) ->
                    val vv = Repo.video(vid)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
                            .background(p.card).border(1.dp, p.line, RoundedCornerShape(10.dp))
                            .clickable { onLesson(vid) }.padding(horizontal = 10.dp, vertical = 9.dp),
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
fun SearchSheet(p: Pal, onLesson: (String) -> Unit, onSeries: (String) -> Unit, onScience: (String) -> Unit, onTimed: (String, Int) -> Unit = { id, _ -> onLesson(id) }) {
    var q by remember { mutableStateOf("") }
    val t = q.trim()
    val vids = remember(t) { Repo.search(t).take(30) }
    var groups by remember { mutableStateOf<List<com.irth.mirath.data.Fts.Group>>(emptyList()) }
    var ftsBusy by remember { mutableStateOf(false) }
    LaunchedEffect(t, com.irth.mirath.data.Fts.count.value / 25) {
        if (t.length < 2) { groups = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(260)
        ftsBusy = true
        groups = runCatching { com.irth.mirath.data.Fts.search(t) }.getOrDefault(emptyList())
        ftsBusy = false
    }
    val sers = remember(t) { if (t.isEmpty()) emptyList() else Repo.series?.filter { it.title.contains(t) }?.take(10) ?: emptyList() }
    val scis = remember(t) { if (t.isEmpty()) emptyList() else Repo.sciences?.filter { it.name.contains(t) } ?: emptyList() }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ابحث: درساً أو سلسلة أو كلمة قالها الشيخ…", color = p.muted) },
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
        FtsCoverage(p)
        if (ftsBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp).height(2.dp), color = p.gold, trackColor = p.card)
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            if (t.isNotEmpty() && !ftsBusy && vids.isEmpty() && sers.isEmpty() && scis.isEmpty() && groups.isEmpty()) {
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
            ftsSection(p, groups, onTimed)
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
