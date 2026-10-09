package com.irth.mirath.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * المكتبة المحلية: ملفات يرفعها المستخدم من الهاتف (ينتجها وكيل التفريغ المعيّن).
 * البنية داخل files/imports/<سلسلة>/<درس>/{transcript.txt,align.json,summary.json,questions.json,media.mp3|mp4}
 * وملف اختياري <سلسلة>/series.json: {"title","science","kind","lessons":{"<id>":{"title","duration"}}}
 */
object LocalLib {
    private var root: File? = null

    fun init(ctx: Context) {
        if (root == null) root = File(ctx.filesDir, "imports").apply { mkdirs() }
    }

    private fun seriesDirs(): List<File> = root?.listFiles()?.filter { it.isDirectory } ?: emptyList()

    private fun meta(slug: String): JsonElement? = runCatching {
        Api.json.parseToJsonElement(File(root, "$slug/series.json").readText())
    }.getOrNull()

    /** سلاسل محلية مدمجة مع كتالوج إرث — علمها «محلي» إن لم يحدّد. */
    fun localSeries(): List<Series> = seriesDirs().map { d ->
        val m = meta(d.name)?.jsonObject
        fun f(k: String) = (m?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
        Series(
            id = "L:${d.name}", title = f("title").ifEmpty { d.name },
            science = f("science").ifEmpty { "local" }, kind = f("kind"),
            items = lessonDirs(d).map { "L:${d.name}/${it.name}" },
        )
    }

    private fun lessonDirs(sd: File): List<File> = sd.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()

    private fun lessonMeta(slug: String, lid: String): Pair<String, Int> {
        val m = meta(slug)?.jsonObject
        val l = (m?.get("lessons")?.jsonObject?.get(lid))?.jsonObject
        val t = (l?.get("title") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
        val du = (l?.get("duration") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
        return (if (t.isNotEmpty()) t else lid) to du
    }

    fun localVideos(): List<Video> = seriesDirs().flatMap { sd ->
        lessonDirs(sd).map { ld ->
            val (title, dur) = lessonMeta(sd.name, ld.name)
            val has = mutableListOf<String>()
            if (File(ld, "transcript.txt").exists()) has += "transcript"
            if (File(ld, "align.json").exists()) has += "align"
            if (File(ld, "summary.json").exists()) has += "summary"
            if (File(ld, "questions.json").exists()) has += "questions"
            if (mediaFile(sd.name, ld.name) != null) has += "media"
            Video(id = "L:${sd.name}/${ld.name}", title = title, duration = dur, has = has)
        }
    }

    fun localScience(): Science? =
        if (localSeries().isNotEmpty()) Science("local", "مكتبتي") else null

    private fun ld(slug: String, lid: String): File = File(root, "$slug/$lid")

    private fun file(id: String, name: String): File? {
        val p = id.removePrefix("L:").split("/", limit = 2)
        if (p.size < 2) return null
        return File(ld(p[0], p[1]), name).takeIf { it.exists() }
    }

    fun transcript(id: String): String? = file(id, "transcript.txt")?.readText()
    fun alignEl(id: String): JsonElement? =
        file(id, "align.json")?.let { runCatching { Api.json.parseToJsonElement(it.readText()) }.getOrNull() }
    fun summaryEl(id: String): JsonElement? =
        file(id, "summary.json")?.let { runCatching { Api.json.parseToJsonElement(it.readText()) }.getOrNull() }
    fun questionsEl(id: String): JsonElement? =
        file(id, "questions.json")?.let { runCatching { Api.json.parseToJsonElement(it.readText()) }.getOrNull() }

    fun mediaFile(slug: String, lid: String): File? =
        ld(slug, lid).listFiles()?.firstOrNull { it.name.startsWith("media.") && it.extension.lowercase() in listOf("mp3", "m4a", "wav", "ogg", "mp4", "webm", "mkv") }

    fun mediaFor(id: String): File? {
        val p = id.removePrefix("L:").split("/", limit = 2)
        return if (p.size < 2) null else mediaFile(p[0], p[1])
    }

    /** هل للسلسلة (أي درس) وسائط محلية تسمح بوضع «محلي»؟ */
    fun seriesHasMedia(seriesId: String): Boolean {
        val slug = seriesId.removePrefix("L:")
        val sd = File(root, slug)
        return lessonDirs(sd).any { mediaFile(slug, it.name) != null }
    }

    fun totalBytes(): Long = root?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0

    /** استيراد شجرة مجلد كامل (منتقي مجلدات النظام). */
    suspend fun importTree(ctx: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val doc = DocumentFile.fromTreeUri(ctx, uri) ?: return@withContext 0
        var n = 0
        fun copy(d: DocumentFile, rel: String) {
            d.listFiles().forEach { f ->
                if (f.isDirectory) copy(f, "$rel/${f.name}")
                else {
                    val name = f.name ?: return@forEach
                    val out = File(root, "$rel/$name")
                    out.parentFile?.mkdirs()
                    runCatching {
                        ctx.contentResolver.openInputStream(f.uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                        n++
                    }
                }
            }
        }
        copy(doc, "")
        n
    }

    /** استيراد ملفات مفردة إلى مجلد «وارد» تحت سلسلة مستعارة. */
    suspend fun importFiles(ctx: Context, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var n = 0
        uris.forEach { u ->
            val d = DocumentFile.fromSingleUri(ctx, u) ?: return@forEach
            val name = d.name ?: return@forEach
            val out = File(root, "وارد/$name")
            out.parentFile?.mkdirs()
            runCatching {
                ctx.contentResolver.openInputStream(u)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                n++
            }
        }
        n
    }
}
