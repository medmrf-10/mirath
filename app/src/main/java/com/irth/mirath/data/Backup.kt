package com.irth.mirath.data

import android.content.Context
import android.net.Uri
import com.irth.mirath.store.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** نسخة احتياطية كاملة: imports/ (سلاسل المستخدم وملفاته) + roadmaps.json + prefs.json (التقدم/العلامات/المفضلة). */
object Backup {

    private fun targets(ctx: Context): List<File> = listOf(
        File(ctx.filesDir, "imports"),
        File(ctx.filesDir, "roadmaps.json"),
    )

    /** يكتب كل شيء في ملف zip على URI يختاره المستخدم. يرجع عدد الملفات المضغوطة. */
    suspend fun export(ctx: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        var n = 0
        ctx.contentResolver.openOutputStream(uri)?.use { os ->
            ZipOutputStream(os.buffered()).use { z ->
                targets(ctx).forEach { f ->
                    if (f.isDirectory) f.walkTopDown().filter { it.isFile }.forEach { sub ->
                        z.putNextEntry(ZipEntry("imports/" + sub.relativeTo(f).path))
                        sub.inputStream().use { it.copyTo(z) }
                        z.closeEntry(); n++
                    } else if (f.isFile) {
                        z.putNextEntry(ZipEntry(f.name))
                        f.inputStream().use { it.copyTo(z) }
                        z.closeEntry(); n++
                    }
                }
                z.putNextEntry(ZipEntry("prefs.json"))
                z.write(Prefs.exportAll().toByteArray())
                z.closeEntry(); n++
            }
        }
        n
    }

    /** يستعيد نسخة: يستخرج imports/ وroadmaps.json ويطبّق prefs.json. يرجع عدد الملفات المستعادة. */
    suspend fun restore(ctx: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        var n = 0
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins.buffered()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        when {
                            e.name.startsWith("imports/") -> {
                                val out = File(ctx.filesDir, e.name)
                                out.parentFile?.mkdirs()
                                out.outputStream().use { z.copyTo(it) }; n++
                            }
                            e.name == "roadmaps.json" -> {
                                File(ctx.filesDir, "roadmaps.json").outputStream().use { z.copyTo(it) }; n++
                            }
                            e.name == "prefs.json" -> {
                                Prefs.importAll(z.readBytes().toString(Charsets.UTF_8)); n++
                            }
                        }
                    }
                    z.closeEntry(); e = z.nextEntry
                }
            }
        }
        n
    }
}
