package com.irth.mirath.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.irth.mirath.data.Repo
import com.irth.mirath.data.Roadmaps
import com.irth.mirath.data.Video
import com.irth.mirath.store.Prefs
import com.irth.mirath.ui.theme.Pal
import kotlin.math.abs

/** تبويب «اليوم» — رفيق التعلم: مواظبة، استئناف، درس مقترح، قيد المشاهدة. */
@Composable
fun TodayScreen(p: Pal, onLesson: (String) -> Unit, onBrowse: () -> Unit) {
    Prefs.streak.value // اشتراك إعادة التركيب عند تغيّر المواظبة
    val streak = Prefs.streakDays()
    val ctx = LocalContext.current
    val vids = Repo.videos ?: emptyList()
    val series = Repo.series ?: emptyList()
    // خريطتي النشطة: الأكثر تقدماً غير المكتملة، وإلا الأولى
    val activeRm = Roadmaps.all(ctx).filter { it.steps.isNotEmpty() }
        .sortedByDescending { Roadmaps.totals(it).first }
        .firstOrNull { Roadmaps.totals(it).let { (d, a) -> d < a } }

    val ongoing = vids.filter { Prefs.ongoing(it.id, it.duration) }
    val resume = ongoing.firstOrNull { it.id == Prefs.last() } ?: ongoing.firstOrNull()

    val suggested: Video? = remember(vids.size) {
        // الدرس التالي غير المُتمّ في سلسلة بدأها المستخدم
        val started = series.filter { s -> s.items.any { Prefs.progress(it) > 10 } }
        var pick: Video? = null
        for (s in started) {
            val nextId = s.items.firstOrNull { id ->
                val v = vids.firstOrNull { it.id == id }
                v != null && !Prefs.finished(id, v.duration) && Prefs.progress(id) <= 10
            }
            if (nextId != null) { pick = vids.firstOrNull { it.id == nextId }; break }
        }
        // لا سلاسل مبدوءة؟ اقتراح يومي ثابت من المكتبة كلها
        if (pick == null && vids.isNotEmpty()) {
            val seed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date()).hashCode()
            pick = vids[abs(seed) % vids.size]
        }
        pick
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(p.soft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = p.gold, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (streak > 0) "مواظبتك: $streak ${if (streak == 1) "يوم" else "أيام"} متتالية" else "ابدأ يومك الأول",
                        color = p.text, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    )
                    Text(
                        if (streak > 0) "استمع اليوم لتبقيها حية" else "استمع لأي درس اليوم وابدأ سلسلة المواظبة",
                        color = p.muted, fontSize = 13.sp,
                    )
                }
            }
        }

        if (resume != null) {
            item {
                val ser = Repo.seriesOfVideo(resume.id)
                val pct = if (resume.duration > 0) (Prefs.progress(resume.id) * 100 / resume.duration) else 0
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card)
                        .clickable { onLesson(resume.id) }.padding(16.dp),
                ) {
                    Text("أكمل المشاهدة", color = p.gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(resume.title, color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (ser != null) Text(ser.title, color = p.muted, fontSize = 13.sp, maxLines = 1)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(p.soft)) {
                        Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().background(p.gold))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("$pct% — تبقّى ${fmtDur(resume.duration - Prefs.progress(resume.id))}", color = p.muted, fontSize = 12.sp)
                }
            }
        }

        if (activeRm != null) {
            item {
                val (rd, ra) = Roadmaps.totals(activeRm)
                val nextId = Roadmaps.nextLesson(activeRm)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card)
                        .clickable { nextId?.let(onLesson) }.padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Map, null, tint = p.gold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("خريطتي — ${activeRm.title}", color = p.gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    nextId?.let { Repo.video(it) }?.let { nv ->
                        Text("الدرس التالي: ${nv.title}", color = p.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(p.soft)) {
                        Box(Modifier.fillMaxWidth(rd.toFloat() / ra.coerceAtLeast(1)).fillMaxHeight().background(p.gold))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("$rd/$ra درساً في المسار", color = p.muted, fontSize = 12.sp)
                }
            }
        }

        if (suggested != null && suggested.id != resume?.id) {
            item {
                val ser = Repo.seriesOfVideo(suggested.id)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card)
                        .clickable { onLesson(suggested.id) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(p.soft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.School, null, tint = p.primary, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("درس اليوم المقترح", color = p.gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(suggested.title, color = p.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (ser != null) Text(ser.title, color = p.muted, fontSize = 12.sp, maxLines = 1)
                    }
                    Icon(Icons.Filled.PlayArrow, "ابدأ", tint = p.gold, modifier = Modifier.size(30.dp))
                }
            }
        }

        val rest = ongoing.filter { it.id != resume?.id }.take(4)
        if (rest.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlaylistPlay, null, tint = p.muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("قيد المشاهدة", color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            items(rest) { v ->
                val ser = Repo.seriesOfVideo(v.id)
                val pv = Prefs.progress(v.id)
                val frac = if (v.duration > 0) (pv.toFloat() / v.duration).coerceIn(0f, 1f) else 0f
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.card)
                        .clickable { onLesson(v.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(v.title, color = p.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (ser?.title ?: "") + " — " + fmtDur(pv) + " / " + fmtDur(v.duration),
                            color = p.muted, fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)).background(p.soft)) {
                            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(p.gold))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Filled.PlayArrow, null, tint = p.primary, modifier = Modifier.size(24.dp))
                }
            }
        }

        if (resume == null && ongoing.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card)
                        .clickable { onBrowse() }.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("ابدأ رحلتك في الميراث", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("تصفح العلوم والسلاسل واختر أول درس", color = p.muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onBrowse() },
                        colors = ButtonDefaults.buttonColors(containerColor = p.primary),
                    ) { Text("تصفح المكتبة") }
                }
            }
        }
    }
}
