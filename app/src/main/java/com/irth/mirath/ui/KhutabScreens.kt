package com.irth.mirath.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irth.mirath.data.Haw
import com.irth.mirath.data.KBlock
import com.irth.mirath.data.KItem
import com.irth.mirath.data.Khutab
import com.irth.mirath.data.Khutba
import com.irth.mirath.data.TextIndex
import com.irth.mirath.store.KFolder
import com.irth.mirath.store.KMix
import com.irth.mirath.store.KStore
import com.irth.mirath.store.MixBlock
import com.irth.mirath.store.Prefs
import com.irth.mirath.store.SavedBlock
import com.irth.mirath.ui.theme.Amiri
import com.irth.mirath.ui.theme.Pal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

private fun copyText(ctx: Context, text: String) {
    runCatching {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText("ميراث", text))
        toast(ctx, "نُسخ النص")
    }
}

private val shape16 = RoundedCornerShape(16.dp)

// ───────────────────────── مكوّنات مشتركة ─────────────────────────

@Composable
private fun SmallAction(p: Pal, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.padding(end = 6.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = p.muted, fontSize = 12.sp)
    }
}

@Composable
private fun PillChip(p: Pal, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 8.dp).clip(RoundedCornerShape(999.dp))
            .background(if (on) p.soft else p.card)
            .border(1.dp, if (on) p.gold else p.line, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = if (on) p.gold else p.muted, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) }
}

/** مفتاح تبديل بين عدة خيارات (أكبر وأوضح من الشرائح). */
@Composable
private fun Segmented(p: Pal, options: List<String>, sel: Int, modifier: Modifier = Modifier, onSel: (Int) -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.soft).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == sel
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .background(if (on) p.card else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, p.gold, RoundedCornerShape(11.dp)) else Modifier)
                    .clickable { onSel(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = if (on) p.gold else p.muted, fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchField(p: Pal, value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.soft).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = p.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = p.text, fontSize = 15.sp),
            cursorBrush = SolidColor(p.gold), modifier = Modifier.weight(1f),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = p.muted, fontSize = 14.sp); inner() } },
        )
        if (value.isNotEmpty()) {
            Icon(Icons.Filled.Close, "مسح", tint = p.muted, modifier = Modifier.size(20.dp).clickable { onChange("") })
        }
    }
}

/** كتلة فكرة واحدة: عنوان + نص + زر حفظ بجانبها؛ تدعم التحديد المتعدد بالضغط المطوّل. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BlockCard(
    p: Pal,
    heading: String,
    text: String,
    saved: Boolean,
    sub: String = "",
    maxLines: Int = Int.MAX_VALUE,
    selected: Boolean = false,
    selecting: Boolean = false,
    onToggleSave: (() -> Unit)? = null,
    onMix: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    extra: (@Composable RowScope.() -> Unit)? = null,
) {
    val fs = Prefs.fontScale.value
    val tapMod = when {
        onLongPress != null || onTap != null -> Modifier.combinedClickable(onClick = { onTap?.invoke() }, onLongClick = onLongPress)
        onOpen != null && maxLines != Int.MAX_VALUE -> Modifier.clickable(onClick = onOpen)
        else -> Modifier
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(shape16)
            .background(if (selected) p.soft else p.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) p.gold else p.line, shape16)
            .then(tapMod)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                if (sub.isNotEmpty()) {
                    Text(
                        sub, color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(heading, color = p.gold, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = (17 * fs).sp, lineHeight = (27 * fs).sp)
            }
            if (selecting) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    if (selected) "محددة" else "غير محددة", tint = if (selected) p.gold else p.muted,
                    modifier = Modifier.padding(top = 10.dp, end = 10.dp).size(26.dp),
                )
            } else if (onToggleSave != null) {
                IconButton(onClick = onToggleSave) {
                    Icon(
                        if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        if (saved) "إزالة الحفظ" else "حفظ الفقرة",
                        tint = if (saved) p.gold else p.muted, modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text, color = p.text, fontFamily = Amiri, fontSize = (19 * fs).sp, lineHeight = (34 * fs).sp,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 8.dp),
        )
        if (!selecting) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onMix != null) SmallAction(p, "إضافة لخطبة مخلوطة", Icons.Filled.Shuffle, onMix)
                if (onCopy != null) SmallAction(p, "نسخ", Icons.Filled.ContentCopy, onCopy)
                extra?.invoke(this)
            }
        }
    }
}

@Composable
private fun NameDialog(p: Pal, title: String, initial: String, confirm: String, onOk: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(title, color = p.text) },
        text = {
            OutlinedTextField(
                value = v, onValueChange = { v = it }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = p.text, unfocusedTextColor = p.text,
                    focusedBorderColor = p.gold, unfocusedBorderColor = p.line, cursorColor = p.gold,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onOk(v); onDismiss() }) { Text(confirm, color = p.gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = p.muted) } },
    )
}

@Composable
private fun ConfirmDialog(p: Pal, msg: String, onOk: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        text = { Text(msg, color = p.text) },
        confirmButton = { TextButton(onClick = { onOk(); onDismiss() }) { Text("حذف", color = p.gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = p.muted) } },
    )
}

private fun folderPath(id: String): String {
    if (id.isEmpty()) return "الجذر"
    val parts = mutableListOf<String>()
    var cur = KStore.folder(id)
    var guard = 0
    while (cur != null && guard++ < 20) { parts.add(0, cur.name); cur = if (cur.parent.isEmpty()) null else KStore.folder(cur.parent) }
    return parts.joinToString(" › ")
}

// ───────────────────────── صفحة «اليوم» ─────────────────────────

private val AUTHOR_FILTERS = listOf("" to "الكل", "omar" to "الحبيب عمر", "sariri" to "السريري", "bouti" to "البوطي", "sayed" to "أحمد السيد", "jifri" to "الحبيب الجفري")

/** الصفحة الرئيسية: ثلاثة أقسام واضحة (الخطب | محفوظاتي | مجلداتي) بمفتاح ثابت أعلى الصفحة. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodayScreen(
    p: Pal,
    onKhutba: (id: String, onlySaved: Boolean) -> Unit,
    onFolder: (String) -> Unit,
    onMix: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var author by rememberSaveable { mutableIntStateOf(0) }
    var q by rememberSaveable { mutableStateOf("") }
    var qs by rememberSaveable { mutableStateOf("") }
    val allItems = Khutab.items.value
    val savedAll = KStore.data.value.saved

    LaunchedEffect(Unit) { Khutab.refresh() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Segmented(
                p, listOf("الخطب", "محفوظاتي" + if (savedAll.isNotEmpty()) " (${savedAll.size})" else "", "مجلداتي"),
                tab, Modifier.padding(horizontal = 16.dp),
            ) { tab = it }
            when (tab) {
                0 -> {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        AUTHOR_FILTERS.forEachIndexed { i, (id, label) ->
                            val n = if (id.isEmpty()) allItems.size else allItems.count { it.author == id }
                            PillChip(p, if (allItems.isEmpty()) label else "$label ($n)", author == i) { author = i }
                        }
                    }
                    SearchField(p, q, "ابحث في عناوين الخطب", Modifier.padding(horizontal = 16.dp)) { q = it }
                }
                1 -> if (savedAll.size > 3) SearchField(p, qs, "ابحث في المحفوظات", Modifier.padding(horizontal = 16.dp)) { qs = it }
            }
        }

        when (tab) {
            0 -> {
                val aid = AUTHOR_FILTERS[author].first
                val list = allItems.filter { aid.isEmpty() || it.author == aid }
                val fq = TextIndex.fold(TextIndex.clean(q))
                val shown = if (fq.isEmpty()) list else list.filter {
                    TextIndex.fold(TextIndex.clean(it.title + " " + it.topic)).contains(fq)
                }
                val last = Prefs.str("lastK").let { id -> allItems.firstOrNull { it.id == id } }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (fq.isEmpty()) {
                        item { DailyCard(p, onKhutba) }
                        if (last != null) item { ContinueCard(p, last, onKhutba) }
                    }
                    if (shown.isEmpty()) item {
                        EmptyBox(p, if (allItems.isEmpty()) "جارٍ تحميل الخطب…" else if (list.isEmpty()) "لا توجد خطب مقسّمة لهذه السلسلة بعد" else "لا نتائج لـ «$q»")
                    }
                    items(shown, key = { it.id }) { it -> KhutbaCard(p, it, it.id == last?.id, onKhutba) }
                }
            }
            1 -> {
                val fq = TextIndex.fold(TextIndex.clean(qs))
                val list = if (fq.isEmpty()) savedAll else savedAll.filter {
                    TextIndex.fold(TextIndex.clean(it.h + " " + it.t + " " + it.kt)).contains(fq)
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (savedAll.isEmpty()) item {
                        EmptyBox(p, "لم تحفظ فقرات بعد — اضغط أيقونة الحفظ بجانب أي فقرة داخل الخطبة، أو اضغط مطولاً لتحديد عدة فقرات")
                    } else if (list.isEmpty()) item {
                        EmptyBox(p, "لا نتائج")
                    } else {
                        list.groupBy { it.k }.forEach { (kid, group) ->
                            stickyHeader(key = "h:$kid") {
                                Row(
                                    Modifier.fillMaxWidth().background(p.bg).clickable { onKhutba(kid, true) }.padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(group.first().kt.ifEmpty { kid }, color = p.gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${group.size} ›", color = p.muted, fontSize = 12.sp)
                                }
                            }
                            items(group, key = { it.b }) { s ->
                                BlockCard(
                                    p, s.h, s.t, saved = true,
                                    onToggleSave = { KStore.toggleSave(s) },
                                    onMix = { mixTargets = listOf(MixBlock(s.k, s.b, s.h, s.t, s.kt)) },
                                    onCopy = { copyText(ctx, s.h + "\n" + s.t + "\n— " + s.kt) },
                                    onOpen = { onKhutba(s.k, false) },
                                )
                            }
                        }
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                FolderBrowser(p, "", onFolder, onMix)
            }
        }
    }

    if (mixTargets.isNotEmpty()) AddToMixSheet(p, mixTargets) { mixTargets = emptyList() }
}

/** هدف «إضافة لخطبة مخلوطة» من تبويب المحفوظات. */
private var mixTargets by mutableStateOf<List<MixBlock>>(emptyList())

@Composable
private fun ContinueCard(p: Pal, last: KItem, onKhutba: (String, Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).appCard(p, { onKhutba(last.id, false) }, highlight = true).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.AutoStories, null, tint = p.gold, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("أكمل القراءة من حيث توقفت", color = p.gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(last.title, color = p.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun KhutbaCard(p: Pal, it: KItem, isLast: Boolean, onKhutba: (String, Boolean) -> Unit) {
    val sc = KStore.savedCount(it.id)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .appCard(p, { onKhutba(it.id, false) }, highlight = isLast).padding(14.dp),
    ) {
        Text(it.title, color = p.text, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 28.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (it.topic.isNotEmpty()) Text(it.topic, color = p.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val who = if (it.author == "omar") "الحبيب عمر" else if (it.author == "sariri") "السريري" else if (it.author == "bouti") "البوطي" else if (it.author == "sayed") "أحمد السيد" else if (it.author == "jifri") "الحبيب الجفري" else ""
            Text(
                listOfNotNull(who.takeIf { w -> w.isNotEmpty() }, "${it.blocks} فقرة", fmtDur(it.dur).takeIf { _ -> it.dur > 0 }).joinToString(" · "),
                color = p.gold, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            if (isLast) Text("آخر قراءة", color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
            if (sc > 0) {
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, p.gold, RoundedCornerShape(999.dp))
                        .clickable { onKhutba(it.id, true) }.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Bookmark, null, tint = p.gold, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$sc", color = p.gold, fontSize = 12.sp)
                }
            }
        }
    }
}

/** فقرة اليوم: اختيار ثابت لكل يوم من الخطب المتاحة. */
@Composable
private fun DailyCard(p: Pal, onKhutba: (String, Boolean) -> Unit) {
    val ctx = LocalContext.current
    val items = Khutab.items.value
    if (items.isEmpty()) return
    val seed = abs(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()).hashCode())
    val item = items[seed % items.size]
    var pick by remember(item.id) { mutableStateOf<Pair<Khutba, KBlock>?>(null) }
    LaunchedEffect(item.id) {
        val k = Khutab.get(item.id)
        if (k != null && k.blocks.isNotEmpty()) pick = k to k.blocks[(seed / 7) % k.blocks.size]
    }
    val (k, b) = pick ?: return
    val savedNow = KStore.data.value.saved.any { it.b == b.id }
    BlockCard(
        p, b.h, b.t, saved = savedNow, sub = "فقرة اليوم · ${k.title}", maxLines = 5,
        onToggleSave = { KStore.toggleSave(SavedBlock(k.id, b.id, b.h, b.t, k.title, k.author)) },
        onCopy = { copyText(ctx, b.h + "\n" + b.t + "\n— " + k.title) },
        onOpen = { onKhutba(k.id, false) },
    )
}

// ───────────────────────── قارئ الخطبة ─────────────────────────

/** صفوف المستند بترتيبه: عنوان ← (لكل خطبة) مقدمة ← العرض ← دعاء ← خاتمة ← التنقل. */
private sealed class RR(val key: String) {
    class Title(val k: Khutba) : RR("title")
    class Part(val part: Int) : RR("part$part")
    class Out(val part: Int, val kind: String, val text: String) : RR("out$part$kind")
    class BodyHdr(val part: Int, val n: Int) : RR("body$part")
    class Blk(val b: KBlock) : RR(b.id)
    class Empty : RR("empty")
    class Nav(val prev: KItem?, val next: KItem?) : RR("nav")
}

private data class Anchor(val part: Int, val kind: String, val index: Int)

private fun kindLabel(kind: String) = when (kind) {
    "intro" -> "المقدمة"
    "body" -> "العرض"
    "dua" -> "الدعاء"
    else -> "الخاتمة"
}

private fun buildRows(k: Khutba, savedMode: Boolean, savedIds: Set<String>, prev: KItem?, next: KItem?): Pair<List<RR>, List<Anchor>> {
    val s = k.sections
    val rows = ArrayList<RR>()
    val anchors = ArrayList<Anchor>()
    rows += RR.Title(k)
    fun txt(x: String?): String? = x?.takeIf { it.isNotBlank() }
    val hasTwo = k.blocks.any { it.part == 2 } || listOf(s.intro2, s.dua2 ?: s.dua, s.close2).any { !it.isNullOrBlank() }
    for (part in if (hasTwo) listOf(1, 2) else listOf(1)) {
        val body = k.blocks.filter { it.part == part && (!savedMode || it.id in savedIds) }
        if (savedMode) {
            if (body.isEmpty()) continue
            if (hasTwo) rows += RR.Part(part)
            body.forEach { rows += RR.Blk(it) }
            continue
        }
        val intro = txt(if (part == 1) (s.intro1 ?: s.intro) else s.intro2)
        val dua = txt(if (part == 1) s.dua1 else (s.dua2 ?: s.dua))
        val close = txt(if (part == 1) s.close1 else s.close2)
        val partIdx = rows.size
        if (hasTwo) rows += RR.Part(part)
        var firstAnchor = true
        fun mark(kind: String) { anchors += Anchor(part, kind, if (firstAnchor) partIdx else rows.size); firstAnchor = false }
        if (intro != null) { mark("intro"); rows += RR.Out(part, "intro", intro) }
        if (body.isNotEmpty()) { mark("body"); rows += RR.BodyHdr(part, body.size); body.forEach { rows += RR.Blk(it) } }
        if (dua != null) { mark("dua"); rows += RR.Out(part, "dua", dua) }
        if (close != null) { mark("close"); rows += RR.Out(part, "close", close) }
    }
    if (savedMode && rows.size == 1) rows += RR.Empty()
    if (!savedMode && (prev != null || next != null)) rows += RR.Nav(prev, next)
    return rows to anchors
}

private fun fullText(k: Khutba): String {
    val rows = buildRows(k, false, emptySet(), null, null).first
    return buildString {
        append(k.title).append("\n\n")
        rows.forEach {
            when (it) {
                is RR.Out -> append("— ").append(kindLabel(it.kind)).append(if (it.part == 2) " (الخطبة الثانية)" else "").append("\n").append(it.text).append("\n\n")
                is RR.Blk -> append(it.b.h).append("\n").append(it.b.t).append("\n\n")
                else -> {}
            }
        }
    }.trim()
}

@Composable
fun KhutbaScreen(p: Pal, id: String, onlySaved: Boolean, onLesson: (String) -> Unit, onKhutba: (String) -> Unit = {}) {
    var k by remember(id) { mutableStateOf<Khutba?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        val r = Khutab.get(id)
        if (r == null) failed = true else { k = r; Prefs.putStr("lastK", id) }
    }
    val kk = k
    if (failed) { EmptyBox(p, "تعذّر تحميل الخطبة — تحقق من الاتصال"); return }
    if (kk == null) { LoadingBox(p); return }
    KhutbaReader(p, kk, onlySaved, onLesson, onKhutba)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KhutbaReader(p: Pal, kk: Khutba, onlySaved: Boolean, onLesson: (String) -> Unit, onKhutba: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedMode by rememberSaveable(kk.id) { mutableStateOf(onlySaved) }
    var expanded by rememberSaveable(kk.id) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var mixFor by remember { mutableStateOf<List<MixBlock>>(emptyList()) }
    val savedIds = KStore.data.value.saved.map { it.b }.toSet()
    val savedCount = kk.blocks.count { it.id in savedIds }

    val siblings = Khutab.itemsOf(kk.author)
    val si = siblings.indexOfFirst { it.id == kk.id }
    val prev = if (si > 0) siblings[si - 1] else null
    val next = if (si >= 0 && si < siblings.lastIndex) siblings[si + 1] else null

    val built = remember(kk.id, savedMode, if (savedMode) savedIds else emptySet<String>(), prev?.id, next?.id) {
        buildRows(kk, savedMode, savedIds, prev, next)
    }
    val rows = built.first
    val anchors = built.second
    val hasTwoStages = anchors.any { it.part == 2 }

    val haptic = rememberHaptic()
    fun toggleSel(id: String) { haptic(selected.isEmpty()); if (id in selected) selected.remove(id) else selected.add(id) }
    fun expKeys() = expanded.split(",").filter { it.isNotEmpty() }
    fun toggleExp(key: String) {
        val l = expKeys().toMutableList()
        if (!l.remove(key)) l.add(key)
        expanded = l.joinToString(",")
    }
    BackHandler(enabled = selected.isNotEmpty()) { selected.clear() }

    val startIdx = remember(kk.id) { if (onlySaved) 0 else (Prefs.str("kpos:${kk.id}").toIntOrNull() ?: 0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIdx.coerceIn(0, (rows.size - 1).coerceAtLeast(0)))
    var modeInit by remember { mutableStateOf(false) }
    LaunchedEffect(savedMode) { if (modeInit) listState.scrollToItem(0) else modeInit = true }
    LaunchedEffect(kk.id, savedMode) {
        if (!savedMode) snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { i -> delay(600); Prefs.putStr("kpos:${kk.id}", i.toString()) }
    }
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val curStage = anchors.indexOfLast { it.index <= firstVisible }.coerceAtLeast(0)
    val navState = rememberLazyListState()
    LaunchedEffect(curStage, savedMode) {
        if (!savedMode && anchors.size > 1) navState.animateScrollToItem((curStage - 1).coerceAtLeast(0))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ── الرأس الثابت: وضع العرض + شريط المراحل
            Column(Modifier.fillMaxWidth().background(p.bg).padding(top = 6.dp, bottom = 6.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Segmented(
                        p, listOf("الخطبة كاملة", "المحفوظات ($savedCount)"), if (savedMode) 1 else 0, Modifier.weight(1f),
                    ) { savedMode = it == 1; selected.clear() }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "المزيد", tint = p.muted) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = p.card) {
                            DropdownMenuItem(text = { Text("تكبير الخط", color = p.text) }, onClick = { Prefs.setFontScale((Prefs.fontScale.value + 0.1f).coerceAtMost(2f)) })
                            DropdownMenuItem(text = { Text("تصغير الخط", color = p.text) }, onClick = { Prefs.setFontScale((Prefs.fontScale.value - 0.1f).coerceAtLeast(0.7f)) })
                            if (!savedMode && anchors.any { it.kind != "body" }) {
                                val all = anchors.filter { it.kind != "body" }.map { "${it.part}:${it.kind}" }
                                val allOpen = all.all { it in expKeys() }
                                DropdownMenuItem(
                                    text = { Text(if (allOpen) "طي المقدمات والأدعية والخواتم" else "فتح المقدمات والأدعية والخواتم", color = p.text) },
                                    onClick = { expanded = if (allOpen) "" else all.joinToString(","); menu = false },
                                )
                            }
                            DropdownMenuItem(text = { Text("نسخ الخطبة كاملة", color = p.text) }, onClick = { menu = false; copyText(ctx, fullText(kk)) })
                        }
                    }
                }
                if (!savedMode && anchors.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(state = navState, contentPadding = PaddingValues(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        itemsIndexed(anchors, key = { _, a -> "${a.part}:${a.kind}" }) { i, a ->
                            val lbl = (if (hasTwoStages) (if (a.part == 1) "١ · " else "٢ · ") else "") + kindLabel(a.kind)
                            PillChip(p, lbl, i == curStage) {
                                scope.launch {
                                    val key = "${a.part}:${a.kind}"
                                    if (a.kind != "body" && key !in expKeys()) toggleExp(key)
                                    listState.animateScrollToItem(a.index)
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = p.line)

            // ── المستند
            LazyColumn(
                state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(rows, key = { it.key }) { r ->
                    when (r) {
                        is RR.Title -> TitleBlock(p, r.k, savedMode, onListen = {
                            scope.launch {
                                runCatching { Haw.ensure("H:" + r.k.src.removePrefix("H:").substringBeforeLast(":")) }
                                onLesson(r.k.src)
                            }
                        })
                        is RR.Part -> PartHeader(p, r.part)
                        is RR.Out -> {
                            val key = "${r.part}:${r.kind}"
                            OutsideCard(p, kindLabel(r.kind), r.text, key in expKeys(), onToggle = { toggleExp(key) }, onCopy = { copyText(ctx, r.text) })
                        }
                        is RR.BodyHdr -> Text(
                            "العرض · ${r.n} فقرة", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        is RR.Blk -> {
                            val b = r.b
                            BlockCard(
                                p, b.h, b.t, saved = b.id in savedIds,
                                selected = b.id in selected, selecting = selected.isNotEmpty(),
                                onToggleSave = {
                                    val now = KStore.toggleSave(SavedBlock(kk.id, b.id, b.h, b.t, kk.title, kk.author))
                                    haptic(false)
                                    toast(ctx, if (now) "حُفظت الفقرة" else "أُزيل الحفظ")
                                },
                                onMix = { mixFor = listOf(MixBlock(kk.id, b.id, b.h, b.t, kk.title)) },
                                onCopy = { copyText(ctx, b.h + "\n" + b.t + "\n— " + kk.title) },
                                onLongPress = { toggleSel(b.id) },
                                onTap = if (selected.isNotEmpty()) ({ toggleSel(b.id) }) else null,
                            )
                        }
                        is RR.Empty -> EmptyBox(p, "لا فقرات محفوظة في هذه الخطبة — اضغط أيقونة الحفظ بجانب أي فقرة")
                        is RR.Nav -> NavCards(p, r.prev, r.next, onKhutba)
                    }
                }
            }
        }

        // ── شريط التحديد المتعدد
        AnimatedVisibility(
            visible = selected.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            val selBlocks = kk.blocks.filter { it.id in selected }
            Row(
                Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).background(p.card)
                    .border(1.dp, p.gold, RoundedCornerShape(18.dp)).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selected.clear() }) { Icon(Icons.Filled.Close, "إلغاء التحديد", tint = p.muted) }
                Text("${selected.size} محددة", color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                BarAction(p, "حفظ", Icons.Filled.Bookmark) {
                    var n = 0
                    selBlocks.filter { it.id !in savedIds }.forEach { b ->
                        KStore.toggleSave(SavedBlock(kk.id, b.id, b.h, b.t, kk.title, kk.author)); n++
                    }
                    toast(ctx, if (n > 0) "حُفظت $n فقرة" else "كلها محفوظة")
                    selected.clear()
                }
                BarAction(p, "مخلوطة", Icons.Filled.Shuffle) {
                    mixFor = selBlocks.map { MixBlock(kk.id, it.id, it.h, it.t, kk.title) }
                }
                BarAction(p, "نسخ", Icons.Filled.ContentCopy) {
                    copyText(ctx, selBlocks.joinToString("\n\n") { it.h + "\n" + it.t } + "\n— " + kk.title)
                    selected.clear()
                }
            }
        }
    }

    if (mixFor.isNotEmpty()) AddToMixSheet(p, mixFor) { mixFor = emptyList(); selected.clear() }
}

@Composable
private fun BarAction(p: Pal, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = p.gold, modifier = Modifier.size(22.dp))
        Text(label, color = p.muted, fontSize = 11.sp)
    }
}

@Composable
private fun TitleBlock(p: Pal, k: Khutba, savedMode: Boolean, onListen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(k.title, color = p.primary, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 36.sp)
        Text(k.authorName, color = p.muted, fontSize = 13.sp)
        if (k.topic.isNotEmpty()) Text(k.topic, color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${k.blocks.size} فقرة" + if (k.dur > 0) " · ${fmtDur(k.dur)}" else "",
                color = p.gold, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            if (k.src.startsWith("H:")) {
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, p.gold, RoundedCornerShape(999.dp))
                        .clickable(onClick = onListen).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = p.gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("استمع للخطبة", color = p.gold, fontSize = 12.sp)
                }
            }
        }
        if (!savedMode) {
            Spacer(Modifier.height(4.dp))
            Text("اضغط مطولاً على فقرة لتحديد عدة فقرات وحفظها أو إضافتها لخطبة مخلوطة دفعة واحدة", color = p.muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PartHeader(p: Pal, part: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(p.line))
        Text(
            if (part == 1) "الخطبة الأولى" else "الخطبة الثانية", color = p.gold, fontFamily = Amiri,
            fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(p.line))
    }
}

/** مقدمة/دعاء/خاتمة: بطاقة مطويّة بمعاينة سطرين وتُفتح مكانها بلمسة. */
@Composable
private fun OutsideCard(p: Pal, label: String, text: String, expanded: Boolean, onToggle: () -> Unit, onCopy: () -> Unit) {
    val fs = Prefs.fontScale.value
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateContentSize().clip(shape16).background(p.soft)
            .clickable(onClick = onToggle).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(p.gold))
            Spacer(Modifier.width(8.dp))
            Text(label, color = p.gold, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, if (expanded) "طي" else "فتح", tint = p.muted)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text, color = if (expanded) p.text else p.muted, fontFamily = Amiri,
            fontSize = (if (expanded) 19 * fs else 16f).sp, lineHeight = (if (expanded) 34 * fs else 26f).sp,
            maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
        )
        if (expanded) Row { SmallAction(p, "نسخ", Icons.Filled.ContentCopy, onCopy) }
    }
}

@Composable
private fun NavCards(p: Pal, prev: KItem?, next: KItem?, onKhutba: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        @Composable
        fun card(target: KItem?, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, mod: Modifier) {
            if (target == null) { Spacer(mod); return }
            Column(mod.clip(shape16).background(p.card).border(1.dp, p.line, shape16).clickable { onKhutba(target.id) }.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = p.gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(label, color = p.gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(target.title, color = p.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        card(prev, "الخطبة السابقة", Icons.Filled.ChevronRight, Modifier.weight(1f))
        card(next, "الخطبة التالية", Icons.Filled.ChevronLeft, Modifier.weight(1f))
    }
}

// ───────────────────────── إضافة فقرات لخطبة مخلوطة ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToMixSheet(p: Pal, blocks: List<MixBlock>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    val mixes = KStore.data.value.mixes
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.card) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Text(
                if (blocks.size > 1) "إضافة ${blocks.size} فقرات إلى خطبة مخلوطة" else "إضافة إلى خطبة مخلوطة",
                color = p.gold, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            // الأحدث أولاً: غالباً تضيف إلى ما تعمل عليه الآن
            mixes.sortedByDescending { it.ts }.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val n = blocks.count { KStore.addToMix(m.id, it) }
                        toast(
                            ctx,
                            when {
                                n == 0 -> "الفقرة موجودة في «${m.title}»"
                                blocks.size == 1 -> "أُضيفت إلى «${m.title}»"
                                else -> "أُضيفت $n فقرة إلى «${m.title}»"
                            },
                        )
                        onDismiss()
                    }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Shuffle, null, tint = p.gold)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.title, color = p.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${m.blocks.size} فقرات · ${folderPath(m.folder)}", color = p.muted, fontSize = 12.sp)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, tint = p.primary)
                Spacer(Modifier.width(12.dp))
                Text("خطبة مخلوطة جديدة", color = p.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (creating) NameDialog(p, "اسم الخطبة المخلوطة", "", "إنشاء", onOk = { name ->
        val id = KStore.newMix(name, "")
        blocks.forEach { KStore.addToMix(id, it) }
        toast(ctx, "أُنشئت الخطبة وأُضيفت الفقرات")
        onDismiss()
    }, onDismiss = { creating = false })
}

// ───────────────────────── المجلدات والخطب المخلوطة ─────────────────────────

private sealed class Dlg {
    object NewFolder : Dlg()
    object NewMix : Dlg()
    data class RenameFolder(val f: KFolder) : Dlg()
    data class RenameMix(val m: KMix) : Dlg()
    data class DelFolder(val f: KFolder) : Dlg()
    data class DelMix(val m: KMix) : Dlg()
}

@Composable
private fun FolderBrowser(p: Pal, folderId: String, onFolder: (String) -> Unit, onMix: (String) -> Unit) {
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    val data = KStore.data.value
    val subs = data.folders.filter { it.parent == folderId }
    val mixes = data.mixes.filter { it.folder == folderId }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { dlg = Dlg.NewFolder }, shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
                border = androidx.compose.foundation.BorderStroke(1.dp, p.line), modifier = Modifier.weight(1f),
            ) { Icon(Icons.Filled.CreateNewFolder, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("مجلد جديد", fontSize = 13.sp) }
            OutlinedButton(
                onClick = { dlg = Dlg.NewMix }, shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
                border = androidx.compose.foundation.BorderStroke(1.dp, p.line), modifier = Modifier.weight(1f),
            ) { Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("خطبة مخلوطة", fontSize = 13.sp) }
        }
        if (subs.isEmpty() && mixes.isEmpty()) {
            EmptyBox(p, "فارغ — أنشئ مجلداً أو خطبة مخلوطة تجمع فيها فقرات من خطب مختلفة")
        }
        subs.forEach { f ->
            val n = data.folders.count { it.parent == f.id } + data.mixes.count { it.folder == f.id }
            EntryRow(p, Icons.Filled.Folder, f.name, "$n عناصر", { onFolder(f.id) },
                onRename = { dlg = Dlg.RenameFolder(f) }, onDelete = { dlg = Dlg.DelFolder(f) })
        }
        mixes.forEach { m ->
            EntryRow(p, Icons.Filled.Shuffle, m.title, "${m.blocks.size} فقرات", { onMix(m.id) },
                onRename = { dlg = Dlg.RenameMix(m) }, onDelete = { dlg = Dlg.DelMix(m) })
        }
    }

    when (val d = dlg) {
        Dlg.NewFolder -> NameDialog(p, "اسم المجلد", "", "إنشاء", { KStore.newFolder(it, folderId) }) { dlg = null }
        Dlg.NewMix -> NameDialog(p, "اسم الخطبة المخلوطة", "", "إنشاء", { onMix(KStore.newMix(it, folderId)) }) { dlg = null }
        is Dlg.RenameFolder -> NameDialog(p, "إعادة تسمية المجلد", d.f.name, "حفظ", { KStore.renameFolder(d.f.id, it) }) { dlg = null }
        is Dlg.RenameMix -> NameDialog(p, "إعادة تسمية الخطبة", d.m.title, "حفظ", { KStore.renameMix(d.m.id, it) }) { dlg = null }
        is Dlg.DelFolder -> ConfirmDialog(p, "حذف المجلد «${d.f.name}» ومجلداته الفرعية؟ (الخطب المخلوطة بداخله تنتقل إلى المجلد الأعلى)", { KStore.deleteFolder(d.f.id) }) { dlg = null }
        is Dlg.DelMix -> ConfirmDialog(p, "حذف الخطبة المخلوطة «${d.m.title}»؟ (الفقرات المحفوظة لا تتأثر)", { KStore.deleteMix(d.m.id) }) { dlg = null }
        null -> {}
    }
}

@Composable
private fun EntryRow(
    p: Pal, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String,
    onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(shape16).background(p.card).border(1.dp, p.line, shape16)
            .clickable(onClick = onClick).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.gold)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, color = p.muted, fontSize = 12.sp)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "خيارات", tint = p.muted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = p.card) {
                DropdownMenuItem(text = { Text("إعادة تسمية", color = p.text) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("حذف", color = p.text) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
fun FolderScreen(p: Pal, id: String, onFolder: (String) -> Unit, onMix: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp)) {
        Text(
            "مجلداتي › " + folderPath(id), color = p.muted, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        FolderBrowser(p, id, onFolder, onMix)
    }
}

// ───────────────────────── محرر الخطبة المخلوطة ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MixScreen(p: Pal, id: String, onKhutba: (String) -> Unit) {
    val ctx = LocalContext.current
    val m = KStore.data.value.mixes.firstOrNull { it.id == id }
    var rename by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf("") }
    var removed by remember { mutableStateOf<Pair<Int, MixBlock>?>(null) }
    LaunchedEffect(removed) { if (removed != null) { delay(7000); removed = null } }
    if (m == null) { EmptyBox(p, "الخطبة المخلوطة غير موجودة"); return }
    val savedIds = KStore.data.value.saved.map { it.b }.toSet()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "head") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.title, color = p.primary, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 36.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { rename = true }) { Icon(Icons.Filled.Edit, "إعادة تسمية", tint = p.muted) }
                }
                Text("${m.blocks.size} فقرات · ${folderPath(m.folder)}", color = p.muted, fontSize = 12.sp)
            }
        }
        item(key = "actions") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { picker = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = p.gold, contentColor = p.bg),
                ) { Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("إضافة فقرات", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                if (m.blocks.isNotEmpty()) OutlinedButton(
                    onClick = { copyText(ctx, m.title + "\n\n" + m.blocks.joinToString("\n\n") { it.h + "\n" + it.t }) },
                    shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
                ) { Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("نسخ الكل", fontSize = 13.sp) }
            }
        }
        removed?.let { (idx, b) ->
            item(key = "undo") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(p.soft).padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("أُزيلت «${b.h}»", color = p.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TextButton(onClick = { KStore.insertInMix(m.id, idx, b); removed = null }) { Text("تراجع", color = p.gold) }
                }
            }
        }
        if (m.blocks.isEmpty()) item(key = "empty") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(shape16).background(p.card).border(1.dp, p.line, shape16).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Shuffle, null, tint = p.gold, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text("الخطبة فارغة", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("أضف فقرات من محفوظاتك، أو حدّد فقرات داخل أي خطبة بالضغط المطوّل ثم «مخلوطة».", color = p.muted, fontSize = 13.sp)
            }
        }
        itemsIndexed(m.blocks, key = { _, b -> b.b }) { i, b ->
            MixRow(
                p, i, m.blocks.size, b, expanded = openId == b.b, saved = b.b in savedIds,
                onToggle = { openId = if (openId == b.b) "" else b.b },
                onUp = { KStore.moveInMix(m.id, b.b, -1) },
                onDown = { KStore.moveInMix(m.id, b.b, 1) },
                onRemove = { removed = i to b; KStore.removeFromMix(m.id, b.b) },
                onCopy = { copyText(ctx, b.h + "\n" + b.t) },
                onOpen = { onKhutba(b.k) },
                onSave = { KStore.toggleSave(SavedBlock(b.k, b.b, b.h, b.t, b.kt)) },
                modifier = Modifier.animateItem(),
            )
        }
    }

    if (rename) NameDialog(p, "إعادة تسمية الخطبة", m.title, "حفظ", { KStore.renameMix(m.id, it) }) { rename = false }
    if (picker) PickSavedSheet(p, m) { picker = false }
}

@Composable
private fun MixRow(
    p: Pal, i: Int, total: Int, b: MixBlock, expanded: Boolean, saved: Boolean,
    onToggle: () -> Unit, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit,
    onCopy: () -> Unit, onOpen: () -> Unit, onSave: () -> Unit, modifier: Modifier,
) {
    val fs = Prefs.fontScale.value
    var menu by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).animateContentSize().clip(shape16).background(p.card)
            .border(1.dp, p.line, shape16).clickable(onClick = onToggle).padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 4.dp).size(26.dp).clip(CircleShape).background(p.soft), contentAlignment = Alignment.Center) {
                Text("${i + 1}", color = p.gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    b.h, color = p.gold, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = (16 * fs).sp, lineHeight = (24 * fs).sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                )
                if (b.kt.isNotEmpty()) Text(b.kt, color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    b.t, color = p.text, fontFamily = Amiri, fontSize = ((if (expanded) 19 else 16) * fs).sp,
                    lineHeight = ((if (expanded) 34 else 26) * fs).sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Column {
                IconButton(onClick = onUp, enabled = i > 0, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.ArrowUpward, "أعلى", tint = if (i > 0) p.gold else p.line)
                }
                IconButton(onClick = onDown, enabled = i < total - 1, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.ArrowDownward, "أسفل", tint = if (i < total - 1) p.gold else p.line)
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.MoreVert, "خيارات", tint = p.muted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = p.card) {
                    DropdownMenuItem(text = { Text("إزالة من الخطبة", color = p.text) }, onClick = { menu = false; onRemove() })
                    DropdownMenuItem(text = { Text("نسخ", color = p.text) }, onClick = { menu = false; onCopy() })
                    DropdownMenuItem(text = { Text(if (saved) "إزالة الحفظ" else "حفظ الفقرة", color = p.text) }, onClick = { menu = false; onSave() })
                    DropdownMenuItem(text = { Text("فتح الخطبة الأصلية", color = p.text) }, onClick = { menu = false; onOpen() })
                }
            }
        }
    }
}

/** اختيار متعدد من المحفوظات بزر إضافة واحد. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickSavedSheet(p: Pal, m: KMix, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    val inMix = m.blocks.map { it.b }.toSet()
    val avail = KStore.data.value.saved.filter { it.b !in inMix }
    val fq = TextIndex.fold(TextIndex.clean(q))
    val shown = if (fq.isEmpty()) avail else avail.filter {
        TextIndex.fold(TextIndex.clean(it.h + " " + it.t + " " + it.kt)).contains(fq)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.card) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("اختر من فقراتك المحفوظة", color = p.gold, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                if (avail.isNotEmpty()) TextButton(onClick = {
                    if (picked.size == shown.size) picked.clear() else { picked.clear(); picked.addAll(shown.map { it.b }) }
                }) { Text(if (picked.size == shown.size && shown.isNotEmpty()) "إلغاء الكل" else "تحديد الكل", color = p.primary, fontSize = 13.sp) }
            }
            if (avail.size > 4) SearchField(p, q, "ابحث في المحفوظات", Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { q = it }
            if (avail.isEmpty()) {
                EmptyBox(p, if (KStore.data.value.saved.isEmpty()) "لا فقرات محفوظة بعد — احفظ فقرات من الخطب أولاً" else "أضفت كل المحفوظات")
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                    items(shown, key = { it.b }) { s ->
                        val on = s.b in picked
                        Row(
                            Modifier.fillMaxWidth().background(if (on) p.soft else Color.Transparent)
                                .clickable { if (on) picked.remove(s.b) else picked.add(s.b) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                                tint = if (on) p.gold else p.muted, modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.h, color = p.gold, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(s.t, color = p.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(s.kt, color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        var n = 0
                        picked.forEach { id ->
                            avail.firstOrNull { it.b == id }?.let { s -> if (KStore.addToMix(m.id, MixBlock(s.k, s.b, s.h, s.t, s.kt))) n++ }
                        }
                        toast(ctx, "أُضيفت $n فقرة")
                        onDismiss()
                    },
                    enabled = picked.isNotEmpty(), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.gold, contentColor = p.bg),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(if (picked.isEmpty()) "اختر فقرات للإضافة" else "إضافة (${picked.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
