package com.irth.mirath.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
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
                fontSize = 25.sp,
                color = p.primary,
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
            .clip(RoundedCornerShape(16.dp))
            .background(p.card)
            .border(1.dp, p.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(if (dim) 0.55f else 1f))
            if (subtitle.isNotEmpty()) Text(subtitle, color = p.muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(if (dim) 0.5f else 1f))
            if (meta.isNotEmpty()) Text(meta, color = p.gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.alpha(if (dim) 0.45f else 1f))
        }
        if (tag.isNotEmpty()) {
            Text(tag, color = p.gold, fontSize = 11.sp, modifier = Modifier
                .border(1.dp, p.gold, RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp))
            Spacer(Modifier.width(8.dp))
        }
        if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = p.muted)
    }
    if (bar > 0f) {
        Box(
            Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp)
                .clip(RoundedCornerShape(3.dp)).background(p.soft),
        ) {
            Box(Modifier.fillMaxWidth(bar.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(p.gold))
        }
        Spacer(Modifier.height(4.dp))
    } else Spacer(Modifier.height(10.dp))
}

@Composable
fun SectionTitle(p: Pal, text: String) {
    Text(text, color = p.gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
fun EmptyBox(p: Pal, text: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = p.muted, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun LoadingBox(p: Pal) {
    Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = p.gold, strokeWidth = 2.dp)
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
