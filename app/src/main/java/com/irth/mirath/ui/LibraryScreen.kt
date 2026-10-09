package com.irth.mirath.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irth.mirath.data.LocalLib
import com.irth.mirath.data.Repo
import com.irth.mirath.ui.theme.Pal
import kotlinx.coroutines.launch

/**
 * مكتبتي — المكتبة المحلية: استيراد ملفات الدروس من الهاتف (التفريغ/المحاذاة/الملخصات/الأسئلة/الوسائط)
 * التي ينتجها وكيل التفريغ المعيّن، ثم تصفّحها كسلاسل تُشغَّل دون اتصال.
 */
@Composable
fun LibraryScreen(p: Pal, onSeries: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var rev by remember { mutableStateOf(0) }
    val local = remember(rev) { LocalLib.localSeries() }

    fun after(n: Int) {
        if (n > 0) { Repo.mergeLocal(); rev++; msg = "استُورد $n ملفاً — أُضيفت لمكتبتك" }
        else msg = "لم تُضَف ملفات"
    }

    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        u?.let { scope.launch { busy = true; after(LocalLib.importTree(ctx, it)); busy = false } }
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { us ->
        if (us.isNotEmpty()) scope.launch { busy = true; after(LocalLib.importFiles(ctx, us)); busy = false }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text(
                "استيراد حزمة دروس",
                color = p.gold, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            Text(
                "اختر مجلد السلسلة الذي جهّزه الوكيل (تفريغ + محاذاة + ملخصات + أسئلة + وسائط) — تندمج تلقائياً وتُشغَّل بلا إنترنت.",
                color = p.muted, fontSize = 13.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { pickTree.launch(null) },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
                    enabled = !busy,
                ) {
                    Icon(Icons.Filled.FolderOpen, null, tint = p.gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("مجلد سلسلة كامل")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { pickFiles.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.muted),
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.soft),
                    enabled = !busy,
                ) {
                    Icon(Icons.Filled.CreateNewFolder, null, tint = p.muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("ملفات مفردة")
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = p.gold, trackColor = p.soft)
            msg?.let {
                Text(it, color = p.gold, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
            val mb = LocalLib.totalBytes() / 1024f / 1024f
            Text(
                "المخزّن محلياً: ${"%.1f".format(mb)} م.ب",
                color = p.muted, fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            )
            HorizontalDivider(color = p.line, modifier = Modifier.padding(vertical = 8.dp))
            Text("سلاسلي المحلية", color = p.gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
        }

        if (local.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AudioFile, null, tint = p.muted, modifier = Modifier.size(36.dp))
                    Text("لا سلاسل محلية بعد", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("عندما يرسل لك الوكيل حزمة سلسلة، استوردها من الأعلى", color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }
        items(local, key = { it.id }) { s ->
            val hasMedia = LocalLib.seriesHasMedia(s.id)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp))
                    .background(p.card).border(1.dp, p.line, RoundedCornerShape(14.dp))
                    .clickable { onSeries(s.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(p.soft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CloudDownload, null, tint = p.gold, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, color = p.text, style = MaterialTheme.typography.bodyMedium)
                    Text("${s.items.size} درس" + if (hasMedia) " · وسائط منزّلة" else "", color = p.muted, fontSize = 12.sp)
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}
