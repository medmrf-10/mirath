package com.irth.mirath.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.irth.mirath.data.AWord
import com.irth.mirath.data.LocalLib
import com.irth.mirath.data.QA
import com.irth.mirath.data.Repo
import com.irth.mirath.data.Summary
import com.irth.mirath.data.buildPhrases
import com.irth.mirath.data.findPhraseIndex
import com.irth.mirath.player.G
import com.irth.mirath.store.Prefs
import com.irth.mirath.ui.theme.Amiri
import com.irth.mirath.ui.theme.Pal
import kotlinx.coroutines.delay

private val SPEEDS = listOf(1.0, 1.25, 1.5, 2.0, 0.75)

private fun fmtT(sec: Double): String {
    val s = sec.toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LessonScreen(
    p: Pal,
    videoId: String,
    at: Int = -1,
    onLesson: (String) -> Unit = {},
    onFollow: (String) -> Unit = onLesson,
) {
    val v = Repo.video(videoId)
    if (v == null) { EmptyBox(p, "الدرس غير موجود"); return }
    val ctx = LocalContext.current

    var mode by remember { mutableStateOf("live") }
    var quizOn by remember { mutableStateOf(false) }
    var words by remember(videoId) { mutableStateOf<List<AWord>?>(null) }
    var phrases by remember(videoId) { mutableStateOf<List<List<AWord>>>(emptyList()) }
    var noAlign by remember(videoId) { mutableStateOf(false) }
    var transcript by remember(videoId) { mutableStateOf<String?>(null) }
    var summary by remember(videoId) { mutableStateOf<Summary?>(null) }
    var questions by remember(videoId) { mutableStateOf<List<QA>?>(null) }
    var pos by remember(videoId) { mutableStateOf(if (at >= 0) at.toDouble() else Prefs.progress(videoId).toDouble()) }
    var dur by remember(videoId) { mutableStateOf(v.duration.toDouble()) }
    val playing = G.playing
    var curPh by remember(videoId) { mutableStateOf(0) }
    var spdI by remember { mutableStateOf(SPEEDS.indexOf(Prefs.speed().toDouble()).coerceAtLeast(0)) }
    var claimed by remember(videoId) { mutableStateOf(false) }
    var showDrawer by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var followRead by remember { mutableStateOf(true) }
    var noteFor by remember { mutableStateOf<com.irth.mirath.store.Bookmark?>(null) }
    var noteTxt by remember { mutableStateOf("") }
    val ctl = G
    val ser = remember(videoId) { Repo.seriesOfVideo(videoId) }
    val media = remember(videoId, Prefs.srcRev.value) {
        LocalLib.mediaFor(videoId)?.takeIf { v.yt.isEmpty() || Prefs.src(ser?.id ?: "") == "local" }
    }

    DisposableEffect(Unit) {
        G.onTime = { t ->
            pos = t
            if (G.dur > 0) dur = G.dur
        }
        onDispose { G.onTime = {} }
    }
    LaunchedEffect(videoId, at) {
        Prefs.markOpened(videoId)
        val same = G.videoId == videoId
        if (!same) {
            if (at >= 0) {
                G.play(ctx, videoId, v.title, v.yt, media, at) {}
            } else if (!G.playLesson(ctx, videoId)) {
                G.play(ctx, videoId, v.title, v.yt, media, Prefs.progress(videoId)) {}
            }
        } else if (at >= 0) {
            ctl.seek(at.toDouble())
        }
        claimed = true
        if (v.has.contains("align")) {
            runCatching {
                val ws = Repo.align(videoId)
                if (ws.isEmpty()) noAlign = true else { words = ws; phrases = buildPhrases(ws) }
            }.onFailure { noAlign = true }
        } else noAlign = true
        if (v.has.contains("transcript")) transcript = Repo.transcript(videoId)
        if (v.has.contains("summary")) summary = Repo.summary(videoId)
        if (v.has.contains("questions")) questions = Repo.questions(videoId)
    }
    // الشاشة تتبع المحرك: إن انتقل للتالي (نهاية/إشعار/سماعة) تنتقل معه
    LaunchedEffect(G.videoId, claimed) {
        val g = G.videoId
        if (claimed && g != null && g != videoId) onFollow(g)
    }
    // نبض دقيقة لعدّاد مؤقت النوم
    var sleepTick by remember { mutableStateOf(0) }
    LaunchedEffect(G.sleepAt) { while (G.sleepAt > 0) { delay(20_000); sleepTick++ } }

    // تحديث الجملة الجارية باستمرار عبر البحث الثنائي
    LaunchedEffect(pos, phrases) {
        if (phrases.isNotEmpty()) {
            val pi = findPhraseIndex(phrases, pos)
            if (pi != curPh) curPh = pi
        }
    }

    Column(Modifier.fillMaxSize()) {
        // شريط الأوضاع
        Row(
            Modifier.fillMaxWidth().height(48.dp).background(p.card).border(1.dp, p.line),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MTab(p, Icons.Outlined.Visibility, "حية", mode == "live") { mode = "live" }
            if (v.has.contains("transcript")) MTab(p, Icons.Outlined.MenuBook, "تفريغ", mode == "read") { mode = "read" }
            if (v.has.contains("questions")) MTab(p, Icons.Outlined.Quiz, "أسئلة", mode == "qu") { mode = "qu" }
            if (v.has.contains("summary")) MTab(p, Icons.Outlined.ShortText, "ملخص", mode == "sum") { mode = "sum" }
            if (v.yt.isNotEmpty()) {
                MTab(p, Icons.Outlined.PlayCircle, "يوتيوب", false) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtu.be/${v.yt}")))
                }
            }
            MTab(p, Icons.Outlined.FormatListBulleted, "الدروس", false) { showDrawer = true }
            if (v.has.contains("align") || v.has.contains("transcript"))
                MTab(p, Icons.Outlined.Search, "بحث", mode == "find") { mode = "find" }
        }

        when (mode) {
            "live" -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    Spacer(Modifier.height(10.dp))
                    GSurface(G.local, p)
                    // شريط التقدم — اسحب للقفز لأي موضع
                    val totalDur = if (G.dur > 0) G.dur else dur
                    if (totalDur > 0) {
                        Slider(
                            value = (pos / totalDur).toFloat().coerceIn(0f, 1f),
                            onValueChange = { ctl.seek(it.toDouble() * totalDur) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = p.gold, activeTrackColor = p.gold, inactiveTrackColor = p.soft),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(fmtDur(pos.toInt()), color = p.muted, fontSize = 11.sp)
                            if (v.alignCoverage > 0) {
                                Text("محاذاة ${v.alignCoverage.toInt()}%", color = p.gold.copy(alpha = 0.8f), fontSize = 11.sp)
                            }
                            Text(fmtDur(totalDur.toInt()), color = p.muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // المسرح الحي ثلاثي السياق (الجملة السابقة + الجملة النشطة بالكشف الذهبي + الجملة التالية)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.card)
                            .border(1.dp, p.line, RoundedCornerShape(18.dp))
                            .padding(horizontal = 18.dp, vertical = 18.dp)
                            .heightIn(min = 168.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when {
                            noAlign -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("المحاذاة الحية قيد التجهيز لهذا الدرس", color = p.muted, fontSize = 14.sp)
                                    if (v.has.contains("transcript")) {
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { mode = "read" },
                                            shape = RoundedCornerShape(999.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, p.gold),
                                        ) {
                                            Text("اقرأ التفريغ الكامل", color = p.gold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                            phrases.isEmpty() -> Text("يُحمّل المحاذاة الحية…", color = p.muted, fontSize = 14.sp)
                            else -> {
                                val pi = curPh.coerceIn(0, phrases.lastIndex)
                                val prevPh = phrases.getOrNull(pi - 1)
                                val ph = phrases[pi]
                                val nextPh = phrases.getOrNull(pi + 1)

                                // السطر السابق (سياق خافت قابل للنقر للرجوع)
                                if (prevPh != null) {
                                    Text(
                                        prevPh.joinToString(" ") { it.w },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = Amiri,
                                            fontSize = (18 * Prefs.fontScale.value).sp,
                                            lineHeight = (28 * Prefs.fontScale.value).sp,
                                        ),
                                        color = p.muted,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .alpha(0.45f)
                                            .clickable { ctl.seek((prevPh.first().s - 0.05).coerceAtLeast(0.0)) }
                                            .padding(bottom = 8.dp),
                                    )
                                }

                                // الجملة الحالية (FlowRow يلتف بسلاسة مع تلوين الكلمة النشطة بالذهبي)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    ph.forEachIndexed { k, w ->
                                        val st = wordStyle(p, w, ph, pos, k)
                                        val isCurrent = st.first == p.gold && st.second >= 0.9f
                                        Text(
                                            w.w,
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontFamily = Amiri,
                                                fontSize = (26 * Prefs.fontScale.value).sp,
                                                lineHeight = (42 * Prefs.fontScale.value).sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            ),
                                            color = st.first,
                                            modifier = Modifier
                                                .alpha(st.second)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isCurrent) p.gold.copy(alpha = 0.12f) else Color.Transparent)
                                                .clickable { ctl.seek((w.s - 0.05).coerceAtLeast(0.0)) }
                                                .padding(horizontal = 3.dp, vertical = 1.dp),
                                        )
                                    }
                                }

                                // السطر التالي (معاينة خافتة قابلة للنقر للقفز للأمام)
                                if (nextPh != null) {
                                    Text(
                                        nextPh.joinToString(" ") { it.w },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = Amiri,
                                            fontSize = (18 * Prefs.fontScale.value).sp,
                                            lineHeight = (28 * Prefs.fontScale.value).sp,
                                        ),
                                        color = p.muted,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .alpha(0.42f)
                                            .clickable { ctl.seek((nextPh.first().s - 0.05).coerceAtLeast(0.0)) }
                                            .padding(top = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // منطقة التحكم الكاملة (سابق / -15ث / تشغيل / +15ث / تالي)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card)
                            .border(1.dp, p.line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { G.skipPrev() },
                                enabled = G.prevId != null || pos > 5.0,
                            ) {
                                Icon(Icons.Filled.SkipPrevious, "السابق", tint = if (G.prevId != null || pos > 5.0) p.text else p.muted.copy(alpha = 0.4f))
                            }
                            Spacer(Modifier.width(6.dp))
                            ChipBtn(p, "‹ 15ث", false) { ctl.seek((pos - 15.0).coerceAtLeast(0.0)) }
                            Spacer(Modifier.width(14.dp))
                            Box(
                                Modifier.size(56.dp).clip(CircleShape).background(p.primary)
                                    .clickable { if (playing) ctl.pause() else ctl.play() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    "تشغيل/إيقاف", tint = p.card, modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            ChipBtn(p, "15ث ›", false) { ctl.seek(pos + 15.0) }
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = { G.skipNext() },
                                enabled = G.nextId != null,
                            ) {
                                Icon(Icons.Filled.SkipNext, "التالي", tint = if (G.nextId != null) p.text else p.muted.copy(alpha = 0.4f))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${fmtT(pos)} / ${fmtT(totalDur)}", color = p.muted, fontSize = 12.sp)
                            if (phrases.isNotEmpty()) Text(" · جملة ${curPh + 1}/${phrases.size}", color = p.muted, fontSize = 11.sp)
                            Spacer(Modifier.weight(1f))
                            ChipBtn(p, "أ−", Prefs.fontScale.value > 0.8f) { Prefs.setFontScale((Prefs.fontScale.value - 0.1f).coerceAtLeast(0.7f)) }
                            ChipBtn(p, "أ+", Prefs.fontScale.value < 1.8f) { Prefs.setFontScale((Prefs.fontScale.value + 0.1f).coerceAtMost(2f)) }
                            IconBtn(p, Icons.Filled.Favorite, Prefs.isFav(videoId)) { Prefs.toggleFav(videoId) }
                            Prefs.bmRev.value
                            val hasBm = Prefs.hasBookmark(videoId)
                            IconBtn(p, if (hasBm) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, hasBm) {
                                if (Prefs.toggleBookmark(videoId, pos.toInt())) {
                                    noteFor = Prefs.bookmarks(videoId).lastOrNull { kotlin.math.abs(it.s - pos.toInt()) <= 3 }
                                    noteTxt = ""
                                }
                            }
                            IconBtn(p, Icons.Filled.Share, false) {
                                val t = pos.toInt()
                                val url = if (v.yt.isNotEmpty()) {
                                    "https://youtu.be/${v.yt}" + if (t > 5) "?t=$t" else ""
                                } else {
                                    "https://github.com/medmrf-10/ytb"
                                }
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${v.title} — ميراث" + (if (t > 5) " (عند ${fmtT(t.toDouble())})" else "") + "\n$url")
                                }
                                ctx.startActivity(Intent.createChooser(send, "مشاركة"))
                            }
                            ChipBtn(p, "×${SPEEDS[spdI].let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }}", spdI != 0) {
                                spdI = (spdI + 1) % SPEEDS.size; ctl.setRate(SPEEDS[spdI])
                            }
                            sleepTick
                            val sl = G.sleepLabel()
                            ChipBtn(p, sl ?: "نوم", sl != null) { G.cycleSleep() }
                        }
                        Box(
                            Modifier.fillMaxWidth().padding(top = 10.dp).height(3.dp)
                                .clip(RoundedCornerShape(999.dp)).background(p.soft),
                        ) {
                            val f = if (totalDur > 0) (pos / totalDur).toFloat().coerceIn(0f, 1f) else 0f
                            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(999.dp)).background(p.gold))
                        }
                    }
                    // عنوان الدرس ومعلومات الشيخ والسلسلة
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.title, color = p.text, style = MaterialTheme.typography.titleLarge)
                            val who = v.speakers.joinToString("، ") { Repo.personName(it) }
                            val sub = listOf(who, ser?.title ?: "").filter { it.isNotEmpty() }.joinToString(" · ")
                            if (sub.isNotEmpty()) Text(sub, color = p.gold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
            "read" -> {
                val readListState = rememberLazyListState()
                LaunchedEffect(curPh, followRead, phrases.size) {
                    if (followRead && phrases.isNotEmpty() && curPh in phrases.indices) {
                        runCatching { readListState.animateScrollToItem((curPh - 1).coerceAtLeast(0)) }
                    }
                }
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChipBtn(p, "أ−", Prefs.fontScale.value > 0.8f) { Prefs.setFontScale((Prefs.fontScale.value - 0.1f).coerceAtLeast(0.7f)) }
                        Spacer(Modifier.width(4.dp))
                        ChipBtn(p, "أ+", Prefs.fontScale.value < 1.8f) { Prefs.setFontScale((Prefs.fontScale.value + 0.1f).coerceAtMost(2f)) }
                        Spacer(Modifier.weight(1f))
                        if (phrases.isNotEmpty()) {
                            ChipBtn(p, if (followRead) "متابعة الصوت ✓" else "متابعة الصوت", followRead) {
                                followRead = !followRead
                            }
                        }
                    }
                    if (phrases.isNotEmpty()) {
                        LazyColumn(
                            state = readListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(phrases) { idx, ph ->
                                val active = idx == curPh
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (active) p.soft else p.card)
                                        .border(1.dp, if (active) p.gold else p.line, RoundedCornerShape(12.dp))
                                        .clickable {
                                            ctl.seek((ph.first().s - 0.05).coerceAtLeast(0.0))
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        fmtT(ph.first().s),
                                        color = if (active) p.gold else p.muted,
                                        fontSize = 12.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.width(48.dp).padding(top = 4.dp),
                                    )
                                    Text(
                                        ph.joinToString(" ") { it.w },
                                        color = if (active) p.gold else p.text,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = Amiri,
                                            fontSize = (19 * Prefs.fontScale.value).sp,
                                            lineHeight = (34 * Prefs.fontScale.value).sp,
                                        ),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(40.dp)) }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                val t = transcript
                                if (t == null) LoadingBox(p)
                                else Text(
                                    t,
                                    color = p.text,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = Amiri,
                                        fontSize = (18 * Prefs.fontScale.value).sp,
                                        lineHeight = (34 * Prefs.fontScale.value).sp,
                                    ),
                                    textAlign = TextAlign.Justify,
                                )
                            }
                            item { Spacer(Modifier.height(40.dp)) }
                        }
                    }
                }
            }
            "sum" -> LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChipBtn(p, "أ−", Prefs.fontScale.value > 0.8f) { Prefs.setFontScale((Prefs.fontScale.value - 0.1f).coerceAtLeast(0.7f)) }
                        Spacer(Modifier.width(6.dp))
                        ChipBtn(p, "أ+", Prefs.fontScale.value < 1.8f) { Prefs.setFontScale((Prefs.fontScale.value + 0.1f).coerceAtMost(2f)) }
                    }
                }
                item {
                    val s = summary
                    if (s == null) LoadingBox(p)
                    else Column(Modifier.padding(top = 8.dp)) {
                        Text(s.summary, color = p.text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = (17 * Prefs.fontScale.value).sp, lineHeight = (30 * Prefs.fontScale.value).sp))
                        s.points.forEach { pt ->
                            Row(Modifier.padding(top = 10.dp)) {
                                Text("•", color = p.gold, modifier = Modifier.padding(end = 8.dp))
                                Text(pt, color = p.muted, style = MaterialTheme.typography.bodyLarge.copy(fontSize = (17 * Prefs.fontScale.value).sp))
                            }
                        }
                    }
                }
            }
            "find" -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ابحث في نص الدرس…", color = p.muted) },
                    singleLine = true,
                    textStyle = TextStyle(color = p.text, fontSize = 15.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = p.gold, unfocusedBorderColor = p.line,
                        cursorColor = p.gold,
                        focusedContainerColor = p.card, unfocusedContainerColor = p.card,
                    ),
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, "مسح", tint = p.muted, modifier = Modifier.size(18.dp))
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                val q = query.trim()
                val nq = com.irth.mirath.data.TextIndex.fold(q)
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        nq.length < 2 -> item {
                            Text("اكتب كلمتين فأكثر — النتيجة تقفز بالصوت لموضعها", color = p.muted, fontSize = 13.sp)
                        }
                        phrases.isNotEmpty() -> {
                            val hits = phrases.filter { ph ->
                                com.irth.mirath.data.TextIndex.fold(ph.joinToString(" ") { it.w }).contains(nq)
                            }
                            if (hits.isEmpty()) item { Text("لا نتائج لـ«$q»", color = p.muted, fontSize = 13.sp) }
                            itemsIndexed(hits.take(60)) { _, ph ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.card)
                                        .clickable { ctl.seek((ph.first().s - 0.05).coerceAtLeast(0.0)); mode = "live" }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(fmtT(ph.first().s), color = p.gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(ph.joinToString(" ") { it.w }, color = p.text, fontSize = 14.sp, maxLines = 2)
                                }
                            }
                        }
                        else -> {
                            val t = transcript.orEmpty()
                            val ats = Regex(Regex.escape(q)).findAll(t).map { it.range.first }.toList()
                            if (ats.isEmpty()) item { Text("لا نتائج لـ«$q»", color = p.muted, fontSize = 13.sp) }
                            itemsIndexed(ats.take(40)) { _, atIdx ->
                                val from = (atIdx - 55).coerceAtLeast(0)
                                val to = (atIdx + q.length + 65).coerceAtMost(t.length)
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.card)
                                        .clickable { mode = "read" }
                                        .padding(12.dp),
                                ) {
                                    Text(
                                        "…" + t.substring(from, to).replace('\n', ' ') + "…",
                                        color = p.text, fontSize = 13.sp, maxLines = 3,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            "qu" -> {
                val qs = questions
                when {
                    qs == null -> LazyColumn(Modifier.fillMaxSize()) { item { LoadingBox(p) } }
                    quizOn -> QuizMode(p, videoId, qs) { quizOn = false }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    Prefs.str("quiz.best.$videoId").takeIf { it.isNotEmpty() }?.let { "أفضل اختبار: $it" } ?: "${qs.size} سؤالاً",
                                    color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f),
                                )
                                MTab(p, Icons.Outlined.Quiz, "اختبر نفسك", false) { quizOn = true }
                            }
                        }
                        itemsIndexed(qs) { i, qa -> QCard(p, videoId, i, qa) }
                        item { Spacer(Modifier.height(40.dp)) }
                    }
                }
            }
        }
    }

    // درج السلسلة
    if (showDrawer && ser != null) {
        ModalBottomSheet(onDismissRequest = { showDrawer = false }, containerColor = p.card) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ser.title, color = p.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${ser.items.indexOf(videoId) + 1} / ${ser.items.size}", color = p.muted, fontSize = 12.sp)
                IconButton(onClick = { showDrawer = false }) { Icon(Icons.Filled.Close, "إغلاق", tint = p.muted) }
            }
            val bms = Prefs.bookmarks(videoId)
            if (bms.isNotEmpty()) {
                Text("علاماتي في هذا الدرس", color = p.gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                    itemsIndexed(bms) { _, b ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { showDrawer = false; ctl.seek((b.s.toDouble() - 0.05).coerceAtLeast(0.0)) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(fmtT(b.s.toDouble()), color = p.gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            Text(b.n.ifEmpty { "علامة" }, color = p.text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            IconButton(onClick = { noteFor = b; noteTxt = b.n }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Edit, "ملاحظة", tint = p.muted, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { Prefs.removeBookmark(videoId, b.s) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Delete, "حذف", tint = p.muted, modifier = Modifier.size(16.dp))
                            }
                        }
                        HorizontalDivider(color = p.line)
                    }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                itemsIndexed(ser.items) { i, vid ->
                    val vv = Repo.video(vid)
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (vid == videoId) p.soft else Color.Transparent)
                            .clickable { showDrawer = false; onLesson(vid) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape)
                                .background(if (vid == videoId) p.primary else p.soft)
                                .border(1.5.dp, p.gold, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (vv != null && Prefs.finished(vid, vv.duration))
                                Icon(Icons.Filled.Check, null, tint = if (vid == videoId) p.card else p.gold, modifier = Modifier.size(14.dp))
                            else
                                Text("${i + 1}", color = if (vid == videoId) p.card else p.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            vv?.title ?: vid, color = p.text, fontSize = 14.sp,
                            modifier = Modifier.weight(1f).alpha(if (vv != null && Prefs.finished(vid, vv.duration)) 0.55f else 1f),
                            maxLines = 2,
                        )
                        if (vv?.has?.contains("align") == true) {
                            Text("حيّ", color = p.gold, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp))
                        }
                        Text(fmtDur(vv?.duration ?: 0), color = p.muted, fontSize = 12.sp)
                    }
                    HorizontalDivider(color = p.line)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // حوار ملاحظة العلامة
    val nb = noteFor
    if (nb != null) {
        AlertDialog(
            onDismissRequest = { noteFor = null },
            containerColor = p.card,
            title = { Text("ملاحظة عند ${fmtT(nb.s.toDouble())}", color = p.text, fontSize = 15.sp) },
            text = {
                OutlinedTextField(
                    value = noteTxt, onValueChange = { noteTxt = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("اكتب ملاحظتك…", color = p.muted) },
                    textStyle = TextStyle(color = p.text, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = p.gold, unfocusedBorderColor = p.line, cursorColor = p.gold,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { Prefs.setBookmarkNote(videoId, nb.s, noteTxt); noteFor = null }) {
                    Text("حفظ", color = p.gold)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteFor = null }) { Text("إلغاء", color = p.muted) }
            },
        )
    }
}

/** لون الكلمة ووضوحها — الكلمات السابقة كاملة، الكلمة الجارية بالذهبي الساطع، والقادمة خافتة. */
private fun wordStyle(p: Pal, w: AWord, ph: List<AWord>, pos: Double, k: Int): Pair<Color, Float> {
    val wi = run {
        var r = -1
        for (j in ph.indices) { if (pos >= ph[j].s && pos < ph[j].e + 0.25) { r = j; break } }
        if (r < 0) { for (j in ph.indices.reversed()) { if (ph[j].s <= pos) { r = j; break } } }
        r
    }
    return when {
        k == wi -> {
            val f = ((pos - w.s) / (w.e - w.s).coerceAtLeast(0.05)).coerceIn(0.0, 1.0)
            if (f > 0.35) p.gold to 1f else p.gold to (0.75f + (f * 0.25).toFloat())
        }
        wi >= 0 && k < wi -> p.text to 1f
        else -> p.text to 0.58f
    }
}

@Composable
private fun RowScope.MTab(p: Pal, ic: ImageVector, label: String, on: Boolean, click: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .clickable(onClick = click)
            .background(if (on) p.soft else Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(ic, label, tint = if (on) p.primary else p.muted, modifier = Modifier.size(22.dp))
        Box(Modifier.fillMaxWidth(0.6f).height(3.dp).background(if (on) p.primary else Color.Transparent))
    }
}

@Composable
private fun IconBtn(p: Pal, ic: ImageVector, on: Boolean, click: () -> Unit) {
    Box(
        Modifier.padding(start = 5.dp).size(32.dp).clip(CircleShape)
            .border(1.dp, if (on) p.gold else p.line, CircleShape).background(p.card)
            .clickable(onClick = click),
        contentAlignment = Alignment.Center,
    ) { Icon(ic, null, tint = if (on) p.gold else p.muted, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun ChipBtn(p: Pal, label: String, on: Boolean, click: () -> Unit) {
    Box(
        Modifier.padding(start = 5.dp).clip(RoundedCornerShape(999.dp))
            .border(1.dp, if (on) p.gold else p.line, RoundedCornerShape(999.dp))
            .clickable(onClick = click).padding(horizontal = 10.dp, vertical = 5.dp),
    ) { Text(label, color = if (on) p.gold else p.muted, fontSize = 12.sp) }
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
            HorizontalDivider(color = p.line, modifier = Modifier.padding(vertical = 10.dp))
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

/** سطح المشغّل في صفحة الدرس: WebView يوتيوب المشترك يُرفَع هنا أثناء العرض (يعود خفياً عند الخروج)، أو واجهة صوتية للمحلي. */
@Composable
fun GSurface(local: Boolean, p: Pal) {
    if (local) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(p.soft),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(p.gold.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text("♪", color = p.gold, fontSize = 30.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("صوت محلي — يُشغَّل دون إنترنت", color = p.muted, style = MaterialTheme.typography.labelMedium)
            }
        }
    } else {
        AndroidView(
            factory = { ctx ->
                android.widget.FrameLayout(ctx).apply {
                    G.webView?.let { wv ->
                        (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                        addView(wv, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color.Black),
        )
    }
}

/** وضع الاختبار: بطاقة واحدة — سؤال، كشف الجواب، تقييم ذاتي (أصبت/أخطأت)، ثم نتيجة تحفظ أفضل محاولة. */
@Composable
fun QuizMode(p: Pal, videoId: String, qs: List<QA>, onExit: () -> Unit) {
    var idx by remember { mutableStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val total = qs.size

    fun advance(hit: Boolean) {
        if (hit) right++
        if (idx >= total - 1) {
            done = true
            val score = right + if (hit) 1 else 0
            val best = Prefs.str("quiz.best.$videoId")
                .split("/").mapNotNull { it.toIntOrNull() }
            if (best.size != 2 || score * best[1] > best[0] * total)
                Prefs.putStr("quiz.best.$videoId", "$score/$total")
        } else { idx++; revealed = false }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("اختبر نفسك", color = p.gold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (done) "انتهى الاختبار" else "س ${idx + 1}/$total · صح $right", color = p.muted, fontSize = 13.sp)
            IconButton(onClick = onExit) { Icon(Icons.Filled.Close, "إنهاء", tint = p.muted) }
        }
        LinearProgressIndicator(
            progress = { if (done) 1f else idx.toFloat() / total },
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(4.dp).clip(RoundedCornerShape(4.dp)),
            color = p.gold, trackColor = p.line,
        )
        if (!done) {
            val qa = qs[idx]
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card)
                    .border(1.dp, p.line, RoundedCornerShape(16.dp)).padding(18.dp),
            ) {
                Text("س ${idx + 1}:", color = p.gold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(qa.q, color = p.text, style = MaterialTheme.typography.bodyLarge)
                if (!revealed) {
                    Button(
                        onClick = { revealed = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.soft, contentColor = p.text),
                    ) { Text("أظهر الجواب") }
                } else {
                    HorizontalDivider(color = p.line, modifier = Modifier.padding(vertical = 12.dp))
                    Text(qa.a, color = p.muted, style = MaterialTheme.typography.bodyLarge)
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { advance(false) },
                            modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = p.muted),
                            border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
                        ) { Text("أخطأت") }
                        Button(
                            onClick = { advance(true) },
                            modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = p.gold, contentColor = Color.Black),
                        ) { Text("أصبت") }
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card)
                    .border(1.dp, p.gold, RoundedCornerShape(16.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$right / $total", color = p.gold, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("إجابات صحيحة", color = p.muted, fontSize = 13.sp)
                Text(
                    "أفضل نتيجة: ${Prefs.str("quiz.best.$videoId")}",
                    color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { idx = 0; revealed = false; right = 0; done = false },
                        modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text),
                        border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
                    ) { Text("أعد الاختبار") }
                    Button(
                        onClick = onExit,
                        modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.primary),
                    ) { Text("للأسئلة") }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
