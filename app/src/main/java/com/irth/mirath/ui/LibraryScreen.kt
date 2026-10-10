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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Map
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
import com.irth.mirath.data.Backup
import com.irth.mirath.data.LocalLib
import com.irth.mirath.data.Repo
import com.irth.mirath.data.Roadmaps
import com.irth.mirath.data.Sync
import com.irth.mirath.ui.theme.Pal
import kotlinx.coroutines.launch

/**
 * مكتبتي — سلاسل المستخدم: ينشئها بنفسه (يدوياً أو من قائمة يوتيوب)، يرتّبها في مجلدات،
 * يستورد حزم الوكيل والملفات المفردة، ويشغّلها بلا إنترنت.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(p: Pal, onSeries: (String) -> Unit, onRoadmap: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var rev by remember { mutableStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var createUrl by remember { mutableStateOf("") }
    var editId by remember { mutableStateOf<String?>(null) }
    var showRm by remember { mutableStateOf(false) }
    var signed by remember { mutableStateOf(Sync.signedIn()) }
    var authCode by remember { mutableStateOf<Sync.DeviceCode?>(null) }
    val local = remember(rev) { LocalLib.localSeries() }
    val rms = remember(rev) { Roadmaps.all(ctx) }

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
    val exportBk = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u ->
        u?.let { scope.launch { busy = true; val n = Backup.export(ctx, it); msg = "صُدّرت نسخة احتياطية كاملة ($n ملفاً)"; busy = false } }
    }
    val restoreBk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { scope.launch { busy = true; val n = Backup.restore(ctx, it); Repo.mergeLocal(); rev++; msg = "استُعيدت النسخة ($n ملفاً) — سلاسلك وتقدمك وعلاماتك رجعت"; busy = false } }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            // إنشاء سلسلة — الإجراء الرئيسي (منصة عامة: المستخدم يبني مكتبته)
            Button(
                onClick = { createUrl = ""; showCreate = true },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.primary),
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("سلسلة جديدة — يدوياً أو من قائمة يوتيوب", fontSize = 15.sp)
            }

            // خرائط الطريق — مسارات تعلم مرتبة عبر السلاسل
            Text(
                "خرائط الطريق",
                color = p.gold, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            )
            rms.forEach { rm ->
                val (rd, ra) = Roadmaps.totals(rm)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                        .background(p.card).border(1.dp, p.line, RoundedCornerShape(14.dp))
                        .clickable { onRoadmap(rm.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(seriesIconOf(rm.icon), null, tint = p.gold, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rm.title, color = p.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text("${rm.steps.size} خطوة · أتممت $rd من $ra", color = p.muted, fontSize = 12.sp)
                    }
                }
            }
            OutlinedButton(
                onClick = { showRm = true },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
                border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
            ) {
                Icon(Icons.Filled.Map, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("خريطة طريق جديدة — مسارك التعليمي بخطوات", fontSize = 14.sp)
            }

            Text(
                "نسخة احتياطية — سلاسلك وملفاتك وتقدمك في ملف واحد",
                color = p.gold, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportBk.launch("mirath-backup.zip") },
                    modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text),
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
                ) { Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("صدّر نسخة", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { restoreBk.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.muted),
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.soft),
                ) { Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("استعد نسخة", fontSize = 13.sp) }
            }

            // حسابي — دخول GitHub (Device Flow) ومزامنة ملفات المستخدم
            Text(
                "حسابي — مزامنة سلاسلك وتقدمك مع GitHub",
                color = p.gold, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            )
            authCode?.let { dc ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                        .background(p.card).border(1.dp, p.gold, RoundedCornerShape(14.dp)).padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("افتح الرابط وأدخل الرمز:", color = p.muted, fontSize = 12.sp)
                    Text(dc.userCode, color = p.gold, fontSize = 26.sp, modifier = Modifier.padding(vertical = 6.dp))
                    Text(dc.verifyUrl, color = p.text, fontSize = 13.sp)
                    OutlinedButton(
                        onClick = { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(dc.verifyUrl))) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("افتح صفحة التفعيل", fontSize = 13.sp) }
                    Text("ينتظر إدخالك الرمز…", color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (Sync.clientId.isEmpty()) {
                Text("مزامنة الريبو تنتظر معرّف تطبيق GitHub — الدخول بجوجل في الطريق", color = p.muted, fontSize = 12.sp)
            } else if (!signed) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true; authCode = Sync.startAuth(); busy = false
                            authCode?.let { dc ->
                                scope.launch {
                                    val t = Sync.pollToken(dc); authCode = null
                                    signed = Sync.signedIn()
                                    msg = if (t != null) "سُجّل الدخول — المزامنة جاهزة" else "لم يكتمل الدخول"
                                }
                            } ?: run { msg = "تعذر بدء الدخول" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
                ) {
                    Icon(Icons.Filled.AccountCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("دخول بحساب GitHub", fontSize = 14.sp)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { scope.launch { busy = true; val n = Sync.push(ctx); msg = if (n >= 0) "رُفعت $n ملفاً لحسابك" else "تعذر الرفع"; busy = false } },
                        modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
                        border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
                    ) { Icon(Icons.Filled.CloudUpload, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("رفع", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = { scope.launch { busy = true; val n = Sync.pull(ctx); rev++; msg = if (n >= 0) "سُحبت $n ملفاً من حسابك" else "تعذر السحب"; busy = false } },
                        modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text),
                        border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
                    ) { Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("سحب", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = { Sync.signOut(); signed = false; msg = "سُجّل الخروج" },
                        modifier = Modifier.weight(0.7f).height(44.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = p.muted),
                        border = androidx.compose.foundation.BorderStroke(1.dp, p.soft),
                    ) { Text("خروج", fontSize = 13.sp) }
                }
            }

            Text(
                "استيراد ملفات",
                color = p.gold, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
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
                    Spacer(Modifier.width(6.dp)); Text("مجلد كامل")
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
            var link by remember { mutableStateOf("") }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = link, onValueChange = { link = it },
                    placeholder = { Text("رابط يوتيوب: فيديو أو قائمة", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = p.gold, unfocusedBorderColor = p.soft,
                        focusedTextColor = p.text, unfocusedTextColor = p.text,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        if (LocalLib.playlistOf(link).isNotEmpty()) {
                            createUrl = link; link = ""; showCreate = true
                        } else scope.launch {
                            busy = true
                            val n = if (LocalLib.importVideoLink(link).isNotEmpty()) 1 else 0
                            link = ""
                            if (n > 0) after(n) else { msg = "الرابط غير صالح" }
                            busy = false
                        }
                    },
                    modifier = Modifier.height(50.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.gold),
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
                    enabled = !busy && link.isNotBlank(),
                ) { Text("أضف") }
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
        }

        if (local.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AudioFile, null, tint = p.muted, modifier = Modifier.size(36.dp))
                    Text("لا سلاسل بعد — أنشئ أولى سلاسلك", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("أو استورد حزمة الوكيل أو رابط قائمة يوتيوب من الأعلى", color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }

        // مجموعات حسب المجلد (متداخلة مثل «فقه/ابن-باز»)
        local.groupBy { it.folder }.toSortedMap().forEach { (fold, list) ->
            if (fold.isNotEmpty()) {
                item(key = "fold:$fold") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, null, tint = p.gold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(fold.replace('/', '›'), color = p.gold, fontSize = 13.sp)
                    }
                }
            }
            items(list, key = { it.id }) { s ->
                val hasMedia = LocalLib.seriesHasMedia(s.id, s.items)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp))
                        .background(p.card).border(1.dp, p.line, RoundedCornerShape(14.dp))
                        .clickable { onSeries(s.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(p.soft), contentAlignment = Alignment.Center) {
                        Icon(seriesIconOf(s.icon.ifEmpty { "book" }), null, tint = p.gold, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = p.text, style = MaterialTheme.typography.bodyMedium)
                        Text("${s.items.size} درس" + if (hasMedia) " · وسائط منزّلة" else "", color = p.muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { editId = s.id }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Edit, "تحرير", tint = p.muted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }

    if (showCreate) {
        ModalBottomSheet(onDismissRequest = { showCreate = false }, containerColor = p.card) {
            CreateSeriesSheet(p, createUrl) { id ->
                showCreate = false; Repo.mergeLocal(); rev++
                onSeries(id)
            }
        }
    }
    editId?.let { eid ->
        ModalBottomSheet(onDismissRequest = { editId = null }, containerColor = p.card) {
            EditSeriesSheet(p, eid) {
                editId = null; Repo.mergeLocal(); rev++
            }
        }
    }
    if (showRm) {
        ModalBottomSheet(onDismissRequest = { showRm = false }, containerColor = p.card) {
            RoadmapEditor(p, ctx, null) { showRm = false; rev++ }
        }
    }
}
