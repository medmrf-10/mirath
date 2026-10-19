package com.irth.mirath.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import com.irth.mirath.data.Haw
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

/** تقسيم ذكي للتفريغ الطويل إلى N أجزاء متوازنة عند حدود الجمل والفقرات دون قطع الكلمات. */
private fun splitTranscriptSmart(text: String, parts: Int): List<String> {
    val clean = text.trim()
    if (clean.isEmpty()) return emptyList()
    if (parts <= 1) return listOf(clean)
    val units = clean.split(Regex("(?<=[.!؟?\\n،؛])\\s+")).filter { it.isNotBlank() }
    if (units.size <= parts) {
        // احتياطي بالتقسيم على الكلمات إذا كان النص بلا علامات ترقيم كافية
        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return listOf(clean)
        val chunkSize = ((words.size + parts - 1) / parts).coerceAtLeast(1)
        return words.chunked(chunkSize).map { it.joinToString(" ") }
    }
    val targetLen = (clean.length / parts).coerceAtLeast(1)
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    for (u in units) {
        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(u.trim())
        if (sb.length >= targetLen && out.size < parts - 1) {
            out += sb.toString().trim()
            sb.clear()
        }
    }
    if (sb.isNotBlank()) out += sb.toString().trim()
    return out
}

private fun copyPlainText(ctx: Context, label: String, text: String) {
    runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(ctx, "$label (${text.length} حرف)", Toast.LENGTH_SHORT).show()
    }
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
    Haw.rev.value
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
    var splitParts by remember(videoId) { mutableStateOf(1) }
    val copiedParts = remember(videoId, splitParts) { mutableStateListOf<Int>() }
    var noteFor by remember { mutableStateOf<com.irth.mirath.store.Bookmark?>(null) }
    var noteTxt by remember { mutableStateOf("") }
    val ctl = G
    val ser = remember(videoId, Haw.rev.value) { Repo.seriesOfVideo(videoId) }
    val media = remember(videoId, Prefs.srcRev.value, Haw.rev.value) {
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
        // ضمان جلب فهرس السلسلة أولاً حتى لا يكون معرف يوتيوب فارغاً أبداً
        if (videoId.startsWith("H:") && videoId.contains(":")) {
            val sid = videoId.substringBeforeLast(":")
            Haw.ensure(sid)
            if (Repo.video(videoId)?.yt.isNullOrEmpty()) {
                Haw.ensure(sid, forceRefresh = true)
            }
        }
        val curV = Repo.video(videoId) ?: v
        if (curV.duration > 0 && dur <= 0.0) dur = curV.duration.toDouble()
        val curMedia = LocalLib.mediaFor(videoId)?.takeIf { curV.yt.isEmpty() || Prefs.src(ser?.id ?: "") == "local" }
        val same = G.videoId == videoId
        if (!same) {
            if (at >= 0) {
                G.play(ctx, videoId, curV.title, curV.yt, curMedia, at) {}
            } else if (!G.playLesson(ctx, videoId)) {
                G.play(ctx, videoId, curV.title, curV.yt, curMedia, Prefs.progress(videoId)) {}
            }
        } else if (at >= 0) {
            ctl.seek(at.toDouble())
        }
        claimed = true
        if (curV.has.contains("align")) {
            runCatching {
                val ws = Repo.align(videoId)
                if (ws.isEmpty()) noAlign = true else { words = ws; phrases = buildPhrases(ws) }
            }.onFailure { noAlign = true }
        } else noAlign = true
        if (curV.has.contains("transcript")) transcript = Repo.transcript(videoId)
        if (curV.has.contains("summary")) summary = Repo.summary(videoId)
        if (curV.has.contains("questions")) questions = Repo.questions(videoId)
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
        if (mode != "live") {
            Box(Modifier.size(1.dp).alpha(0f)) { GSurface(G.local, p) }
        }

        when (mode) {
            "live" -> BoxWithConstraints(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val maxVideoHeight = maxHeight * 0.35f
                val totalDur = if (G.dur > 0) G.dur else dur
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 1) عنوان الدرس ومعلومات الشيخ في سطر ثابت أعلى المشغل
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            v.title,
                            color = p.text,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val who = v.speakers.joinToString("، ") { Repo.personName(it) }
                        val sub = listOf(who, ser?.title ?: "").filter { it.isNotEmpty() }.joinToString(" · ")
                        if (sub.isNotEmpty()) {
                            Text(
                                sub,
                                color = p.gold,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // 2) سطح الفيديو بارتفاع مضبوط لا يزاحم بقية الشاشة
                    Box(
                        Modifier.fillMaxWidth().heightIn(max = maxVideoHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        GSurface(G.local, p)
                    }

                    // 3) شريط التقدم المدمج
                    if (totalDur > 0) {
                        Column(Modifier.fillMaxWidth()) {
                            Slider(
                                value = (pos / totalDur).toFloat().coerceIn(0f, 1f),
                                onValueChange = { ctl.seek(it.toDouble() * totalDur) },
                                modifier = Modifier.fillMaxWidth().height(26.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = p.gold,
                                    activeTrackColor = p.gold,
                                    inactiveTrackColor = p.soft,
                                ),
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(fmtDur(pos.toInt()), color = p.muted, fontSize = 11.sp)
                                if (phrases.isNotEmpty()) {
                                    Text("${curPh + 1} / ${phrases.size}", color = p.muted, fontSize = 11.sp)
                                }
                                Text(fmtDur(totalDur.toInt()), color = p.muted, fontSize = 11.sp)
                            }
                        }
                    }

                    // 4) المسرح الحي الثابت — يعرض الجملة الحالية فقط بتركيز هادئ وبدون سكرول
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(p.card)
                            .border(1.dp, p.line, RoundedCornerShape(18.dp))
                            .clipToBounds()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
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
                                val ph = phrases[pi]
                                val charLen = ph.sumOf { it.w.length + 1 }
                                val baseSp = when {
                                    charLen > 120 -> 20f
                                    charLen > 85 -> 22f
                                    else -> 25f
                                } * Prefs.fontScale.value
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
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
                                                fontSize = baseSp.sp,
                                                lineHeight = (baseSp * 1.55f).sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            ),
                                            color = st.first,
                                            modifier = Modifier
                                                .alpha(st.second)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isCurrent) p.gold.copy(alpha = 0.14f) else Color.Transparent)
                                                .clickable { ctl.seek((w.s - 0.05).coerceAtLeast(0.0)) }
                                                .padding(horizontal = 3.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5) شريط التحكم السفلي الثابت
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(p.card)
                            .border(1.dp, p.line, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { G.skipPrev() },
                                enabled = G.prevId != null || pos > 5.0,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Filled.SkipPrevious, "السابق", tint = if (G.prevId != null || pos > 5.0) p.text else p.muted.copy(alpha = 0.4f))
                            }
                            Spacer(Modifier.width(6.dp))
                            ChipBtn(p, "‹ 15ث", false) { ctl.seek((pos - 15.0).coerceAtLeast(0.0)) }
                            Spacer(Modifier.width(12.dp))
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(p.primary)
                                    .clickable { if (playing) ctl.pause() else ctl.play() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    "تشغيل/إيقاف",
                                    tint = p.card,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            ChipBtn(p, "15ث ›", false) { ctl.seek(pos + 15.0) }
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = { G.skipNext() },
                                enabled = G.nextId != null,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Filled.SkipNext, "التالي", tint = if (G.nextId != null) p.text else p.muted.copy(alpha = 0.4f))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                    }
                }
            }
            "read" -> {
                val rawText = remember(transcript, phrases) {
                    val t = transcript?.trim().orEmpty()
                    if (t.isNotEmpty()) t
                    else if (phrases.isNotEmpty()) phrases.joinToString(" ") { ph -> ph.joinToString(" ") { it.w } }
                    else ""
                }
                val chunks = remember(rawText, splitParts) {
                    splitTranscriptSmart(rawText, splitParts)
                }

                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    // شريط أدوات القراءة والتقسيم والنسخ
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.card)
                            .border(1.dp, p.line, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChipBtn(p, "أ−", Prefs.fontScale.value > 0.8f) { Prefs.setFontScale((Prefs.fontScale.value - 0.1f).coerceAtLeast(0.7f)) }
                            Spacer(Modifier.width(4.dp))
                            ChipBtn(p, "أ+", Prefs.fontScale.value < 1.8f) { Prefs.setFontScale((Prefs.fontScale.value + 0.1f).coerceAtMost(2f)) }
                            Spacer(Modifier.weight(1f))
                            Text("تقسيم النسخ:", color = p.muted, fontSize = 12.sp)
                            listOf(1 to "كامل", 3 to "3", 5 to "5", 10 to "10").forEach { (n, lbl) ->
                                ChipBtn(p, lbl, splitParts == n) { splitParts = n }
                            }
                        }

                        if (rawText.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            if (splitParts == 1) {
                                val copiedAll = 0 in copiedParts
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (copiedAll) p.gold.copy(alpha = 0.16f) else p.soft)
                                        .border(1.dp, if (copiedAll) p.gold else p.line, RoundedCornerShape(10.dp))
                                        .clickable {
                                            copyPlainText(ctx, "تم نسخ التفريغ كاملاً", rawText)
                                            if (0 !in copiedParts) copiedParts.add(0)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (copiedAll) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        tint = p.gold,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (copiedAll) "تم نسخ المقال كاملاً ✓ (${rawText.length} حرف)" else "نسخ المقال كاملاً (${rawText.length} حرف)",
                                        color = if (copiedAll) p.gold else p.text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            } else {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    chunks.forEachIndexed { idx, partText ->
                                        val isCopied = idx in copiedParts
                                        val partLabel = "جزء ${idx + 1}/${chunks.size}"
                                        Row(
                                            Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(if (isCopied) p.gold.copy(alpha = 0.16f) else p.soft)
                                                .border(1.dp, if (isCopied) p.gold else p.line, RoundedCornerShape(999.dp))
                                                .clickable {
                                                    val header = "[${v.title} — $partLabel]\n\n"
                                                    copyPlainText(ctx, "تم نسخ $partLabel", header + partText)
                                                    if (idx !in copiedParts) copiedParts.add(idx)
                                                }
                                                .padding(horizontal = 11.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                if (isCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                                contentDescription = null,
                                                tint = if (isCopied) p.gold else p.muted,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                if (isCopied) "نسخ ${idx + 1}/${chunks.size} ✓" else "نسخ ${idx + 1}/${chunks.size}",
                                                color = if (isCopied) p.gold else p.text,
                                                fontSize = 12.sp,
                                                fontWeight = if (isCopied) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // عرض المقال المتصل النظيف بدون مؤقتات أو تقطيع عشوائي
                    if (rawText.isEmpty() && transcript == null) {
                        LoadingBox(p)
                    } else {
                        SelectionContainer {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(p.card)
                                    .border(1.dp, p.line, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                if (splitParts <= 1) {
                                    item {
                                        Text(
                                            rawText,
                                            color = p.text,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontFamily = Amiri,
                                                fontSize = (20 * Prefs.fontScale.value).sp,
                                                lineHeight = (38 * Prefs.fontScale.value).sp,
                                            ),
                                            textAlign = TextAlign.Justify,
                                        )
                                    }
                                } else {
                                    itemsIndexed(chunks) { idx, partText ->
                                        val isCopied = idx in copiedParts
                                        Column(Modifier.fillMaxWidth()) {
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 6.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(p.soft)
                                                    .clickable {
                                                        val partLabel = "جزء ${idx + 1}/${chunks.size}"
                                                        val header = "[${v.title} — $partLabel]\n\n"
                                                        copyPlainText(ctx, "تم نسخ $partLabel", header + partText)
                                                        if (idx !in copiedParts) copiedParts.add(idx)
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    "الجزء ${idx + 1} من ${chunks.size} (${partText.length} حرف)",
                                                    color = p.gold,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Text(
                                                    if (isCopied) "تم النسخ ✓" else "اضغط لنسخ هذا الجزء",
                                                    color = if (isCopied) p.gold else p.muted,
                                                    fontSize = 11.sp,
                                                )
                                            }
                                            Text(
                                                partText,
                                                color = p.text,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontFamily = Amiri,
                                                    fontSize = (20 * Prefs.fontScale.value).sp,
                                                    lineHeight = (38 * Prefs.fontScale.value).sp,
                                                ),
                                                textAlign = TextAlign.Justify,
                                            )
                                            if (idx < chunks.lastIndex) {
                                                HorizontalDivider(color = p.line, modifier = Modifier.padding(top = 12.dp))
                                            }
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(32.dp)) }
                            }
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
                    val wv = G.ensureWeb(ctx)
                    if (wv.parent !== this) {
                        (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                        addView(wv, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                }
            },
            update = { frame ->
                val wv = G.ensureWeb(frame.context)
                if (wv.parent !== frame) {
                    (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                    frame.addView(wv, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
                }
            },
            onRelease = { frame ->
                frame.removeAllViews()
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
