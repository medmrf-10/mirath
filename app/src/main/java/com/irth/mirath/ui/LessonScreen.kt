package com.irth.mirath.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irth.mirath.data.AWord
import com.irth.mirath.data.QA
import com.irth.mirath.data.LocalLib
import com.irth.mirath.data.Repo
import com.irth.mirath.data.Summary
import com.irth.mirath.data.buildPhrases
import com.irth.mirath.player.Playback
import androidx.compose.runtime.saveable.rememberSaveable
import com.irth.mirath.store.Prefs
import com.irth.mirath.ui.theme.Amiri
import com.irth.mirath.ui.theme.Pal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SPEEDS = listOf(1.0, 1.25, 1.5, 2.0, 0.75)
private val SLEEPS = listOf(10, 20, 30)

private fun fmtT(sec: Double): String {
    val s = sec.toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(p: Pal, videoId: String, onLesson: (String) -> Unit = {}) {
    val v = Repo.video(videoId)
    if (v == null) { EmptyBox(p, "الدرس غير موجود"); return }
    val ctx = LocalContext.current

    var mode by remember { mutableStateOf("live") }
    var words by remember { mutableStateOf<List<AWord>?>(null) }
    var phrases by remember { mutableStateOf<List<List<AWord>>>(emptyList()) }
    var noAlign by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<Summary?>(null) }
    var questions by remember { mutableStateOf<List<QA>?>(null) }
    val isCur = Playback.cur.value == videoId
    val pos = if (isCur) Playback.pos.value else Prefs.progress(videoId).toDouble()
    val dur = if (isCur && Playback.dur.value > 0) Playback.dur.value else v.duration.toDouble()
    val playing = isCur && Playback.playing.value
    var curPh by remember { mutableStateOf(0) }
    val spdI = SPEEDS.indexOf(Playback.speed.value).coerceAtLeast(0)
    val slpI = SLEEPS.indexOf(Playback.sleepMin.value)
    var showDrawer by remember { mutableStateOf(false) }
    val ser = remember(videoId) { Repo.series?.firstOrNull { it.items.contains(videoId) } }
    var claimedRev by rememberSaveable(videoId) { mutableStateOf(-1) }
    val srcRev = Prefs.srcRev.value

    LaunchedEffect(videoId) {
        Prefs.markOpened(videoId)
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
    // المحرك العالمي: فتح الدرس مرة واحدة لكل دخول (لا عند الرجوع إليه من المكدّس)، والمتابعة دون إعادة إن كان جارياً
    LaunchedEffect(videoId, srcRev) {
        if (claimedRev != srcRev) { claimedRev = srcRev; Playback.open(ctx, videoId) }
    }
    // الانتقال التلقائي للدرس التالي يتم في المحرك — والصفحة تتبعه إن كانت تعرض الدرس المنتهي
    val adv = Playback.advanced.value
    LaunchedEffect(adv) {
        if (adv != null && adv.first == videoId) { Playback.advanced.value = null; onLesson(adv.second) }
    }

    Column(Modifier.fillMaxSize()) {
        // شريط الأوضاع — أيقوني مثل lmodes
        Row(
            Modifier.fillMaxWidth().height(48.dp).background(p.card).border(1.dp, p.line),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MTab(p, Icons.Outlined.Visibility, "حية", mode == "live") { mode = "live" }
            if (v.has.contains("transcript")) MTab(p, Icons.Outlined.MenuBook, "تفريغ", mode == "read") { mode = "read" }
            if (v.has.contains("questions")) MTab(p, Icons.Outlined.Quiz, "أسئلة", mode == "qu") { mode = "qu" }
            if (v.has.contains("summary")) MTab(p, Icons.Outlined.ShortText, "ملخص", mode == "sum") { mode = "sum" }
            MTab(p, Icons.Outlined.PlayCircle, "يوتيوب", false) {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtu.be/${v.yt}")))
            }
            MTab(p, Icons.Outlined.FormatListBulleted, "الدروس", false) { showDrawer = true }
        }

        when (mode) {
            "live" -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    Spacer(Modifier.height(10.dp))
                    LessonPlayer(p, videoId, v.yt)
                    Spacer(Modifier.height(10.dp))

                    // المسرح الحي — الجملة الحالية بالكشف الذهبي
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card)
                            .border(1.dp, p.line, RoundedCornerShape(16.dp))
                            .padding(horizontal = 18.dp, vertical = 22.dp)
                            .heightIn(min = 96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            noAlign -> Text("لا محاذاة لهذا الدرس — جرّب «تفريغ»", color = p.muted, fontSize = 14.sp)
                            phrases.isEmpty() -> Text("يُحمّل…", color = p.muted, fontSize = 14.sp)
                            else -> {
                                val pi = phraseIndex(phrases, pos)
                                if (pi != curPh) curPh = pi
                                val ph = phrases[pi]
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    ph.forEachIndexed { k, w ->
                                        val st = wordStyle(p, w, ph, pos, k)
                                        Text(
                                            w.w,
                                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Amiri, fontSize = 26.sp, lineHeight = 40.sp),
                                            color = st.first,
                                            modifier = Modifier
                                                .alpha(st.second)
                                                .clickable { if (isCur) Playback.seek(w.s - 0.05) }
                                                .padding(horizontal = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // منطقة التحكم
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card)
                            .border(1.dp, p.line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Box(
                                Modifier.size(56.dp).clip(CircleShape).background(p.primary)
                                    .clickable { if (isCur) Playback.toggle() else Playback.open(ctx, videoId, autoplay = true) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    "تشغيل/إيقاف", tint = p.card, modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${fmtT(pos)} / ${fmtT(dur)}", color = p.muted, fontSize = 13.sp)
                            if (phrases.isNotEmpty()) Text("  جملة ${curPh + 1} / ${phrases.size}", color = p.muted, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            IconBtn(p, Icons.Filled.Favorite, Prefs.isFav(videoId)) { Prefs.toggleFav(videoId) }
                            IconBtn(p, Icons.Filled.Share, false) {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${v.title} — إرث\nhttps://medmrf-10.github.io/irth/#/lesson/$videoId")
                                }
                                ctx.startActivity(Intent.createChooser(send, "مشاركة"))
                            }
                            ChipBtn(p, "×${SPEEDS[spdI].let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }}", spdI != 0) {
                                Playback.setSpeed(SPEEDS[(spdI + 1) % SPEEDS.size])
                            }
                            ChipBtn(p, if (slpI >= 0) "نوم ${SLEEPS[slpI]}" else "نوم", slpI >= 0) {
                                val n = (slpI + 1) % (SLEEPS.size + 1)
                                Playback.setSleep(if (n < SLEEPS.size) SLEEPS[n] else 0)
                            }
                        }
                        Box(
                            Modifier.fillMaxWidth().padding(top = 10.dp).height(3.dp)
                                .clip(RoundedCornerShape(999.dp)).background(p.soft),
                        ) {
                            val f = if (dur > 0) (pos / dur).toFloat().coerceIn(0f, 1f) else 0f
                            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(999.dp)).background(p.gold))
                        }
                    }
                    // عنوان الدرس
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.title, color = p.text, style = MaterialTheme.typography.titleLarge)
                            val who = v.speakers.joinToString("، ") { Repo.personName(it) }
                            if (who.isNotEmpty()) Text(who, color = p.gold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
            "read" -> LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                item {
                    val t = transcript
                    if (t == null) LoadingBox(p)
                    else Text(t, color = p.text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Justify, lineHeight = 32.sp)
                }
            }
            "sum" -> LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                item {
                    val s = summary
                    if (s == null) LoadingBox(p)
                    else Column {
                        Text(s.summary, color = p.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 30.sp)
                        s.points.forEach { pt ->
                            Row(Modifier.padding(top = 10.dp)) {
                                Text("•", color = p.gold, modifier = Modifier.padding(end = 8.dp))
                                Text(pt, color = p.muted, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            "qu" -> LazyColumn(Modifier.fillMaxSize()) {
                val qs = questions
                if (qs == null) item { LoadingBox(p) }
                else {
                    itemsIndexed(qs) { i, qa -> QCard(p, videoId, i, qa) }
                    item { Spacer(Modifier.height(40.dp)) }
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
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                itemsIndexed(ser.items) { i, vid ->
                    val vv = Repo.video(vid)
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (vid == videoId) p.soft else Color.Transparent)
                            .clickable { showDrawer = false; onLesson(vid) }
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                            .border(0.dp, Color.Transparent),
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
                        Text(fmtDur(vv?.duration ?: 0), color = p.muted, fontSize = 12.sp)
                    }
                    Divider(color = p.line)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** موقع الجملة بنفس منطق tick في إرث. */
private fun phraseIndex(phrases: List<List<AWord>>, pos: Double): Int {
    if (phrases.isEmpty()) return 0
    for (i in phrases.indices) {
        if (pos <= phrases[i].last().e + 0.4) return i
    }
    return phrases.size - 1
}

/** لون الكلمة ووضوحها — .w خافتة .62، .read كاملة، الجارية بتدرّج ذهبي حسب التقدم. */
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
            if (f > 0.55) p.gold to 1f else p.gold to (0.62f + (f * 0.5).toFloat())
        }
        wi >= 0 && k < wi -> p.text to 1f
        else -> p.text to 0.62f
    }
}

@Composable
private fun RowScope.MTab(p: Pal, ic: ImageVector, label: String, on: Boolean, click: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .clickable(onClick = click)
            .background(if (on) p.soft else Color.Transparent)
            .border(0.dp, Color.Transparent),
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
        Modifier.padding(start = 6.dp).size(32.dp).clip(CircleShape)
            .border(1.dp, if (on) p.gold else p.line, CircleShape).background(p.card)
            .clickable(onClick = click),
        contentAlignment = Alignment.Center,
    ) { Icon(ic, null, tint = if (on) p.gold else p.muted, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun ChipBtn(p: Pal, label: String, on: Boolean, click: () -> Unit) {
    Box(
        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(999.dp))
            .border(1.dp, if (on) p.gold else p.line, RoundedCornerShape(999.dp))
            .clickable(onClick = click).padding(horizontal = 12.dp, vertical = 5.dp),
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
