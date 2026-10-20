package com.irth.mirath.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irth.mirath.ui.theme.Amiri
import com.irth.mirath.ui.theme.Pal

@Composable
fun TopGlassBar(p: Pal, title: String, onTheme: () -> Unit, onSearch: () -> Unit, onBack: (() -> Unit)? = null) {
    Surface(color = p.bg.copy(alpha = 0.92f), tonalElevation = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "رجوع", tint = p.muted)
                }
            }
            Text(
                title,
                fontFamily = Amiri,
                fontWeight = FontWeight.Bold,
                fontSize = if (title.length > 26) 17.sp else if (title.length > 14) 21.sp else 25.sp,
                color = p.primary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "بحث", tint = p.muted) }
            IconButton(onClick = onTheme) { Icon(Icons.Filled.LightMode, "الشكل", tint = p.muted) }
        }
    }
}

@Composable
fun ThemeSheet(p: Pal, current: String, onPick: (String) -> Unit) {
    val items = listOf(
        "night" to Triple("ليل", Color(0xFF14110E), Icons.Filled.DarkMode),
        "paper" to Triple("ورق", Color(0xFFF4EEE2), Icons.Filled.MenuBook),
        "white" to Triple("أبيض", Color.White, Icons.Filled.LightMode),
        "dark" to Triple("داكن", Color(0xFF18191A), Icons.Filled.Contrast),
    )
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        items.forEach { (k, meta) ->
            val (name, sw, ic) = meta
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(k) }
                    .background(if (k == current) p.press else Color.Transparent)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(sw).border(1.dp, p.line, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(name, color = p.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (k == current) Icon(Icons.Filled.Check, null, tint = p.gold)
            }
        }
    }
}

@Composable
fun RowCard(
    p: Pal,
    index: Int,
    title: String,
    subtitle: String = "",
    meta: String = "",
    tag: String = "",
    dim: Boolean = false,
    bar: Float = 0f,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    var vis by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vis = true }
    val a by animateFloatAsState(if (vis) 1f else 0f, tween(400, delayMillis = index * 45))
    val y by animateFloatAsState(if (vis) 0f else 14f, tween(400, delayMillis = index * 45))
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = a; translationY = y }
            .appCard(p, onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(if (dim) 0.55f else 1f))
            if (subtitle.isNotEmpty()) Text(subtitle, color = p.muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(if (dim) 0.5f else 1f))
            if (meta.isNotEmpty()) Text(meta, color = p.gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.alpha(if (dim) 0.45f else 1f))
            if (bar > 0f) ThinBar(p, bar, Modifier.padding(top = 8.dp))
        }
        if (tag.isNotEmpty()) {
            Text(tag, color = p.gold, fontSize = 11.sp, modifier = Modifier
                .border(1.dp, p.gold, RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp))
            Spacer(Modifier.width(8.dp))
        }
        if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = p.muted)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
fun SectionTitle(p: Pal, text: String) {
    Text(text, color = p.gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
fun EmptyBox(p: Pal, text: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Inbox, null, tint = p.muted.copy(alpha = 0.7f), modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = p.muted, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

/** حالة فارغة بأيقونة وعنوان وشرح وزر إجراء اختياري. */
@Composable
fun EmptyState(p: Pal, icon: ImageVector, title: String, sub: String = "", actionLabel: String = "", onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(p.soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.gold, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = p.text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(sub, color = p.muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        if (actionLabel.isNotEmpty() && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAction, shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.gold, contentColor = p.bg),
            ) { Text(actionLabel, fontWeight = FontWeight.Bold) }
        }
    }
}

// ───────────────────────── نظام التصميم المشترك ─────────────────────────

/** بطاقة بعمق خفيف (تدرّج + حدّ) وتأثير ضغط مرن؛ onClick اختياري. */
@Composable
fun Modifier.appCard(p: Pal, onClick: (() -> Unit)? = null, shape: Shape = RoundedCornerShape(18.dp), highlight: Boolean = false): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed && onClick != null) 0.975f else 1f, tween(110), label = "press")
    return this
        .graphicsLayer { scaleX = sc; scaleY = sc }
        .clip(shape)
        .background(Brush.verticalGradient(listOf(lerp(p.card, p.text, 0.055f), p.card)))
        .border(1.dp, if (highlight) p.gold else p.line, shape)
        .then(if (onClick != null) Modifier.clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick) else Modifier)
}

/** عنوان صفحة مدمج: عنوان كبير + سطر شرح، محاذى للبداية (يمين). */
@Composable
fun PageHeader(p: Pal, title: String, subtitle: String = "", modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp)) {
        Text(title, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp, color = p.text)
        if (subtitle.isNotEmpty()) Text(subtitle, color = p.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

/** صورة مصغّرة لفيديو يوتيوب (16:9) مع خلفية بديلة. */
@Composable
fun Thumb(p: Pal, yt: String, modifier: Modifier = Modifier, label: String = "") {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(p.soft), contentAlignment = Alignment.Center) {
        if (label.isNotEmpty()) Text(label, color = p.gold, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        if (yt.isNotEmpty()) coil.compose.AsyncImage(
            model = "https://i.ytimg.com/vi/$yt/mqdefault.jpg", contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
    }
}

/** شريط تقدم رفيع. */
@Composable
fun ThinBar(p: Pal, frac: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(p.soft)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(p.gold))
    }
}

/** اهتزاز لمسي خفيف لتأكيد فعل (تحديد، حفظ، تبديل). */
@Composable
fun rememberHaptic(): (strong: Boolean) -> Unit {
    val h = LocalHapticFeedback.current
    return remember(h) { { strong -> h.performHapticFeedback(if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove) } }
}

@Composable
fun LoadingBox(p: Pal) {
    Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = p.gold, strokeWidth = 2.dp)
    }
}

/** هيكل تحميل ينبض بلطف بدل دائرة الانتظار. */
@Composable
fun SkeletonHome(p: Pal) {
    val inf = rememberInfiniteTransition(label = "sk")
    val a by inf.animateFloat(
        initialValue = 0.35f, targetValue = 0.8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable<Float>(
            tween(900), androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "sk",
    )
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        repeat(6) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).alpha(a),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(p.soft))
                Spacer(Modifier.width(12.dp))
                Column {
                    Box(Modifier.fillMaxWidth(0.7f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(p.soft))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.4f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(p.soft))
                }
            }
        }
    }
}

/** شاشة خطأ مع زر إعادة — تُستخدم عند فشل إقلاع المكتبة. */
@Composable
fun ErrorBox(p: Pal, title: String, sub: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.CloudOff, null, tint = p.muted, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = p.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(sub, color = p.muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
            border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
        ) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("أعد المحاولة") }
    }
}

@Composable
fun PhIcon(p: Pal, ch: String, size: Int = 56, done: Boolean = false) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(p.soft)
            .border(2.dp, p.gold.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Filled.Check, "مُتمّ", tint = p.gold, modifier = Modifier.size((size * 0.4).dp))
        else Text(ch, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = (size * 0.42).sp, color = p.primary)
    }
}

/** جمع عربي مثل pl3 في إرث: pl3(n,"درس","درسان","دروس","درساً") */
fun pl3(n: Int, one: String, two: String, few: String, many: String): String = when {
    n == 1 -> one
    n == 2 -> two
    n in 3..10 -> "$n $few"
    else -> "$n $many"
}

fun fmtDur(sec: Int): String {
    val h = sec / 3600; val m = (sec % 3600) / 60
    return if (h > 0) "${h}س ${m}د" else "${m}د"
}
