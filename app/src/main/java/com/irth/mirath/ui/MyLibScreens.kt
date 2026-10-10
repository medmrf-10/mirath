package com.irth.mirath.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irth.mirath.data.Backup
import com.irth.mirath.data.LocalLib
import com.irth.mirath.ui.theme.Pal
import kotlinx.coroutines.launch

/** خريطة أيقونات السلاسل — يختارها المستخدم. */
val SERIES_ICONS: List<Pair<String, ImageVector>> = listOf(
    "book" to Icons.Filled.MenuBook,
    "school" to Icons.Filled.School,
    "headset" to Icons.Filled.Headset,
    "mic" to Icons.Filled.Mic,
    "star" to Icons.Filled.Star,
    "bookmark" to Icons.Filled.Bookmark,
    "article" to Icons.Filled.Article,
    "folder" to Icons.Filled.Folder,
    "mosque" to Icons.Filled.Home,
    "heart" to Icons.Filled.Favorite,
    "map" to Icons.Filled.Map,
    "route" to Icons.Filled.Route,
)

fun seriesIconOf(key: String): ImageVector =
    SERIES_ICONS.firstOrNull { it.first == key }?.second ?: Icons.Filled.MenuBook

@Composable
fun IconPicker(p: Pal, sel: String, onSel: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SERIES_ICONS.take(10).forEach { (k, ic) ->
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(if (sel == k) p.soft else p.card)
                    .border(1.5.dp, if (sel == k) p.gold else p.line, CircleShape)
                    .clickable { onSel(k) },
                contentAlignment = Alignment.Center,
            ) { Icon(ic, k, tint = if (sel == k) p.gold else p.muted, modifier = Modifier.size(17.dp)) }
        }
    }
}

@Composable
fun Field(p: Pal, v: String, hint: String, on: (String) -> Unit) {
    OutlinedTextField(
        value = v, onValueChange = on,
        placeholder = { Text(hint, fontSize = 13.sp, color = p.muted) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp), singleLine = true,
        textStyle = TextStyle(color = p.text, fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.gold, unfocusedBorderColor = p.line,
            cursorColor = p.gold, focusedContainerColor = p.card, unfocusedContainerColor = p.card,
        ),
    )
}

/** محرر قائمة الدروس — عدّل العنوان، بدّل الترتيب، احذف، أرفق ملفاً. */
@Composable
private fun LessonEditor(
    p: Pal,
    items: MutableList<Pair<String, String>>,   // (مفتاح/ytId, العنوان)
    attachPick: ((Int) -> Unit)? = null,
) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
        itemsIndexed(items) { i, (key, title) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp)).background(p.card)
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", color = p.gold, fontSize = 12.sp, modifier = Modifier.width(20.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { items[i] = key to it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = p.text, fontSize = 13.sp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedBorderColor = p.line,
                    ),
                )
                if (attachPick != null) {
                    IconButton(onClick = { attachPick(i) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.AttachFile, "أرفق", tint = p.muted, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = { if (i > 0) items.add(i - 1, items.removeAt(i)) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, "أعلى", tint = p.muted, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { if (i < items.lastIndex) items.add(i + 1, items.removeAt(i)) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, "أسفل", tint = p.muted, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { items.removeAt(i) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, "احذف", tint = p.muted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** إنشاء سلسلة جديدة: يدوياً أو من رابط قائمة يوتيوب (مع تحرير العناوين والترتيب). */
@Composable
fun CreateSeriesSheet(p: Pal, initialUrl: String = "", onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("book") }
    var folder by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(initialUrl) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val items = remember { mutableStateListOf<Pair<String, String>>() }
    var fetched by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text("سلسلة جديدة", color = p.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(12.dp))
        Field(p, title, "عنوان السلسلة", { title = it })
        Spacer(Modifier.height(10.dp))
        Text("أيقونة السلسلة", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        IconPicker(p, icon) { icon = it }
        Spacer(Modifier.height(10.dp))
        Field(p, folder, "المجلد (اختياري) — مثل: فقه/ابن-باز", { folder = it })
        Spacer(Modifier.height(12.dp))

        if (!fetched) {
            Text("من قائمة يوتيوب (اختياري)", color = p.muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Field(p, url, "رابط قائمة التشغيل", { url = it }) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            val (t, its) = LocalLib.fetchPlaylist(url)
                            if (its.isNotEmpty()) {
                                items.clear(); items.addAll(its)
                                if (title.isBlank()) title = t
                                fetched = true; msg = null
                            } else msg = "تعذر جلب القائمة — تحقق من الرابط"
                            busy = false
                        }
                    },
                    enabled = !busy && LocalLib.playlistOf(url).isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
                ) { Text(if (busy) "…" else "اجلب", fontSize = 13.sp) }
            }
            msg?.let { Text(it, color = p.gold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    if (title.isBlank()) { msg = "اكتب عنوان السلسلة"; return@Button }
                    val id = LocalLib.createSeries(title, icon, folder)
                    onDone(id)
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.primary),
            ) { Text("أنشئ سلسلة فارغة") }
        } else {
            Text("حلقات القائمة — عدّل العناوين والترتيب قبل الحفظ", color = p.gold, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            LessonEditor(p, items)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (title.isBlank()) { msg = "اكتب عنوان السلسلة"; return@Button }
                    if (items.isEmpty()) { msg = "لا حلقات"; return@Button }
                    val id = LocalLib.createFromPlaylist(title, icon, folder, items.toList())
                    onDone(id)
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.primary),
            ) { Text("أنشئ السلسلة (${items.size} حلقة)") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** تحرير سلسلة مستخدم قائمة: العنوان + الأيقونة + عناوين الدروس وترتيبها + إرفاق ملفات لأي درس. */
@Composable
fun EditSeriesSheet(p: Pal, seriesId: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val meta = remember { LocalLib.seriesMeta(seriesId) }
    var title by remember { mutableStateOf(meta.title) }
    var icon by remember { mutableStateOf(meta.icon.ifEmpty { "book" }) }
    val items = remember { mutableStateListOf<Pair<String, String>>() }
    var attachIdx by remember { mutableStateOf(-1) }
    var msg by remember { mutableStateOf<String?>(null) }
    var confirmDel by remember { mutableStateOf(false) }

    LaunchedEffect(seriesId) {
        items.clear(); items.addAll(LocalLib.lessonsOf(seriesId))
    }

    val pickAttach = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let {
            val lids = items.map { it.first }
            if (attachIdx in lids.indices) scope.launch {
                LocalLib.attachTo(ctx, seriesId, lids[attachIdx], it)
                msg = "أُرفق الملف بالدرس"
            }
        }
    }
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u ->
        u?.let {
            scope.launch {
                val n = Backup.exportSeries(ctx, seriesId, it)
                msg = if (n > 0) "صُدّرت السلسلة ($n ملفاً) — شاركها مع من تريد، ويستوردها بنفس آلية الاستيراد" else "لا ملفات للتصدير"
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text("تحرير السلسلة", color = p.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(12.dp))
        Field(p, title, "عنوان السلسلة", { title = it })
        Spacer(Modifier.height(10.dp))
        IconPicker(p, icon) { icon = it }
        Spacer(Modifier.height(12.dp))
        Text("الدروس — عدّل، رتّب، أرفق ملفات لأي درس", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        LessonEditor(p, items, attachPick = { attachIdx = it; pickAttach.launch(arrayOf("*/*")) })
        msg?.let { Text(it, color = p.gold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                val safe = seriesId.removePrefix("L:").substringAfterLast('/').replace(Regex("[^\\w\\u0600-\\u06FF-]"), "_")
                exportZip.launch("mirath-$safe.zip")
            },
            modifier = Modifier.fillMaxWidth().height(38.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
        ) {
            Icon(Icons.Filled.Share, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("صدّر هذه السلسلة zip — شاركها أو احفظها", fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { confirmDel = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = p.muted),
            ) { Text("حذف السلسلة", fontSize = 12.sp) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    LocalLib.setSeriesInfo(seriesId, title, icon)
                    LocalLib.setLessons(seriesId, items.toList())
                    onDone()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.primary),
            ) { Text("احفظ") }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDel) {
        AlertDialog(
            onDismissRequest = { confirmDel = false },
            containerColor = p.card,
            title = { Text("حذف «${title}» وكل ملفاتها؟", color = p.text, fontSize = 15.sp) },
            confirmButton = {
                TextButton(onClick = { confirmDel = false; LocalLib.deleteSeries(seriesId); onDone() }) {
                    Text("احذف", color = p.gold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("إلغاء", color = p.muted) } },
        )
    }
}
