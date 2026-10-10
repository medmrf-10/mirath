package com.irth.mirath.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irth.mirath.data.RStep
import com.irth.mirath.data.Repo
import com.irth.mirath.data.Roadmap
import com.irth.mirath.data.Roadmaps
import com.irth.mirath.store.Prefs
import com.irth.mirath.ui.theme.Pal

/** محرر خريطة: عنوان/أيقونة/وصف + خطوات باختيار سلاسل (كتالوج أو محلية) مع ترتيب. */
@Composable
fun RoadmapEditor(p: Pal, ctx: android.content.Context, rm0: Roadmap?, onDone: () -> Unit) {
    var title by remember { mutableStateOf(rm0?.title ?: "") }
    var icon by remember { mutableStateOf(rm0?.icon ?: "map") }
    var desc by remember { mutableStateOf(rm0?.desc ?: "") }
    var steps by remember { mutableStateOf(rm0?.steps ?: emptyList()) }
    var pickOpen by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(if (rm0 == null) "خريطة طريق جديدة" else "تحرير الخريطة", style = MaterialTheme.typography.titleLarge, color = p.text, modifier = Modifier.padding(bottom = 10.dp))
        Field(p, title, "اسم الخريطة — مثال: مسار العقيدة") { title = it }
        Spacer(Modifier.height(8.dp))
        Field(p, desc, "وصف اختياري — لماذا هذه الخريطة؟") { desc = it }
        Spacer(Modifier.height(10.dp))
        IconPicker(p, icon) { icon = it }
        Spacer(Modifier.height(12.dp))
        Text("خطوات المسار (${steps.size})", color = p.gold, fontSize = 13.sp)
        steps.forEachIndexed { i, st ->
            val sr = Repo.seriesOf(st.series)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                    .background(p.card).border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", color = p.gold, fontSize = 13.sp, modifier = Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(sr?.title ?: st.series, color = p.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    sr?.let { Text(pl3(it.items.size, "درس", "درسان", "دروس", "درساً"), color = p.muted, fontSize = 11.sp) }
                }
                IconButton(onClick = { if (i > 0) steps = steps.toMutableList().apply { add(i - 1, removeAt(i)) } }) { Icon(Icons.Filled.KeyboardArrowUp, null, tint = p.muted) }
                IconButton(onClick = { if (i < steps.lastIndex) steps = steps.toMutableList().apply { add(i + 1, removeAt(i)) } }) { Icon(Icons.Filled.KeyboardArrowDown, null, tint = p.muted) }
                IconButton(onClick = { steps = steps.toMutableList().apply { removeAt(i) } }) { Icon(Icons.Filled.Close, null, tint = p.muted) }
            }
        }
        if (pickOpen) {
            Field(p, q, "ابحث عن سلسلة لإضافتها…") { q = it }
            val all = Repo.series ?: emptyList()
            val hits = if (q.isBlank()) all.take(12) else all.filter { it.title.contains(q, true) }.take(12)
            LazyColumn(Modifier.fillMaxWidth().height(220.dp).padding(top = 6.dp)) {
                itemsIndexed(hits) { _, sr ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                            .background(p.card).border(1.dp, p.line, RoundedCornerShape(10.dp))
                            .clickable { steps = steps + RStep(sr.id); pickOpen = false; q = "" }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(seriesIconOf(sr.icon), null, tint = p.gold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(sr.title, color = p.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(pl3(sr.items.size, "درس", "درسان", "دروس", "درساً"), color = p.muted, fontSize = 11.sp)
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = { pickOpen = true },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
                border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
            ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("أضف سلسلة للمسار") }
        }
        Button(
            onClick = {
                if (title.isNotBlank()) {
                    Roadmaps.add(ctx, Roadmap(rm0?.id ?: "rm" + System.currentTimeMillis(), title.trim(), icon, desc.trim(), steps))
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(46.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.gold, contentColor = p.bg),
            enabled = title.isNotBlank() && steps.isNotEmpty(),
        ) { Text("احفظ الخريطة", fontSize = 15.sp) }
    }
}

/** شاشة خريطة طريق: الخطوات مرتبة مع حالة كل خطوة وزر الاستئناف. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapScreen(p: Pal, ctx: android.content.Context, rmId: String, onBack: () -> Unit, onSeries: (String) -> Unit, onLesson: (String) -> Unit) {
    var rev by remember { mutableIntStateOf(0) }
    Prefs.streak.value // إعادة تركيب عند تغيّر التقدم
    val rm = remember(rmId, rev) { Roadmaps.get(ctx, rmId) }
    if (rm == null) { EmptyBox(p, "الخريطة غير موجودة"); return }
    val (done, all) = Roadmaps.totals(rm)
    val next = Roadmaps.nextLesson(rm)
    var editOpen by remember { mutableStateOf(false) }
    var delAsk by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(seriesIconOf(rm.icon), null, tint = p.gold, modifier = Modifier.size(40.dp))
                Text(rm.title, style = MaterialTheme.typography.displayLarge, color = p.text, modifier = Modifier.padding(top = 6.dp))
                if (rm.desc.isNotBlank()) Text(rm.desc, color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                Text(if (all == 0) "لا دروس" else "أتممت $done من $all", color = p.gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                if (all > 0) {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(5.dp).clip(RoundedCornerShape(5.dp)).background(p.soft)) {
                        Box(Modifier.fillMaxWidth(done.toFloat() / all).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(p.gold))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (next != null) {
                    OutlinedButton(
                        onClick = { onLesson(next) },
                        modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
                        border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
                    ) { Icon(Icons.Filled.PlayArrow, null, tint = p.gold); Spacer(Modifier.width(6.dp)); Text("أكمل من حيث توقفت") }
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedButton(
                    onClick = { editOpen = true }, modifier = Modifier.height(44.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.muted),
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.soft),
                ) { Text("تحرير", fontSize = 13.sp) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { delAsk = true }, modifier = Modifier.height(44.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.muted),
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.soft),
                ) { Icon(Icons.Filled.Close, null, tint = p.muted, modifier = Modifier.size(16.dp)) }
            }
        }
        itemsIndexed(rm.steps) { i, st ->
            val sr = Repo.seriesOf(st.series)
            val sItems = sr?.items ?: emptyList()
            val sDone = sItems.count { vid -> Repo.video(vid)?.let { Prefs.finished(vid, it.duration) } == true }
            val finished = sItems.isNotEmpty() && sDone == sItems.size
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp))
                    .background(p.card).border(1.dp, if (finished) p.gold else p.line, RoundedCornerShape(14.dp))
                    .clickable { onSeries(st.series) }.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (finished) Icons.Filled.CheckCircle else if (sDone > 0) Icons.Filled.Flag else Icons.Filled.RadioButtonUnchecked,
                    null, tint = if (finished || sDone > 0) p.gold else p.muted, modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${i + 1}. ${sr?.title ?: st.series}", color = p.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (sItems.isEmpty()) "سلسلة فارغة" else "$sDone/${sItems.size} مُتمّ",
                        color = if (finished) p.gold else p.muted, fontSize = 12.sp,
                    )
                }
                if (sItems.isNotEmpty() && sDone in 1 until sItems.size) {
                    Box(Modifier.width(56.dp).height(4.dp).clip(RoundedCornerShape(4.dp)).background(p.soft)) {
                        Box(Modifier.fillMaxWidth(sDone.toFloat() / sItems.size).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(p.gold))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (editOpen) {
        ModalBottomSheet(onDismissRequest = { editOpen = false; rev++ }, containerColor = p.bg) {
            RoadmapEditor(p, ctx, rm) { editOpen = false; rev++ }
        }
    }
    if (delAsk) {
        AlertDialog(
            onDismissRequest = { delAsk = false }, containerColor = p.card,
            title = { Text("حذف الخريطة؟", color = p.text) },
            text = { Text("تُحذف «${rm.title}» فقط — السلاسل والدروس تبقى في مكتبتك.", color = p.muted) },
            confirmButton = {
                TextButton(onClick = { Roadmaps.del(ctx, rmId); delAsk = false; onBack() }) { Text("احذف", color = p.gold) }
            },
            dismissButton = { TextButton(onClick = { delAsk = false }) { Text("إلغاء", color = p.muted) } },
        )
    }
}
