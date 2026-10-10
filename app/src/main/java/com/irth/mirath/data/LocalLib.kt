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
            val yt = File(ld, "yt.txt").takeIf { it.exists() }?.readText()?.trim()
                ?: ytOf(File(ld, "link.txt").takeIf { it.exists() }?.readText()?.trim() ?: "")
            if (yt.isNotEmpty()) has += "yt"
            Video(id = "L:${sd.name}/${ld.name}", title = title, duration = dur, has = has, yt = yt)
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

    /**
     * استيراد ملفات مفردة بأي نوع — الاسم يُصنّفه تلقائياً:
     * <مفتاح>.transcript.txt / .align.json / .summary.json / .questions.json / media.mp3|mp4 / yt.txt
     * أو بأسمائها القياسية. ملفات المفتاح الواحد تندمج في درس واحد تحت «وارد» ولو وصلت على دفعات.
     */
    suspend fun importFiles(ctx: Context, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var n = 0
        uris.forEach { u ->
            val d = DocumentFile.fromSingleUri(ctx, u) ?: return@forEach
            val name = d.name ?: return@forEach
            val (key, canon) = classify(name)
            val out = File(root, "وارد/$key/$canon")
            out.parentFile?.mkdirs()
            runCatching {
                ctx.contentResolver.openInputStream(u)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                n++
            }
        }
        n
    }

    /** تصنيف اسم ملف إلى (مفتاح الدرس, الاسم القياسي). */
    fun classify(name: String): Pair<String, String> {
        val low = name.lowercase()
        val strip = Regex("\\.(transcript|align|summary|questions|media|yt|link)\\.(txt|json|mp3|m4a|wav|ogg|mp4|webm|mkv)$")
        val m = strip.find(low)
        return if (m != null) {
            val key = name.substring(0, m.range.first)
            val kind = m.groupValues[1]
            val ext = m.groupValues[2]
            key to canonName(kind, ext)
        } else when {
            low in setOf("transcript.txt","align.json","summary.json","questions.json") -> "درس-وارد" to name.lowercase()
            low.endsWith(".mp3") || low.endsWith(".m4a") || low.endsWith(".wav") || low.endsWith(".ogg")
                || low.endsWith(".mp4") || low.endsWith(".webm") || low.endsWith(".mkv") ->
                name.substringBeforeLast('.') to "media." + low.substringAfterLast('.')
            else -> name.substringBeforeLast('.') to name
        }
    }

    private fun canonName(kind: String, ext: String): String = when (kind) {
        "transcript" -> "transcript.txt"; "align" -> "align.json"
        "summary" -> "summary.json"; "questions" -> "questions.json"
        "media" -> "media.$ext"; "yt", "link" -> "yt.txt"; else -> "$kind.$ext"
    }

    /** استخراج معرف فيديو يوتيوب من رابط أو نص. */
    fun ytOf(text: String): String {
        val m = Regex("(?:youtu\\.be/|v=|shorts/|embed/)([\\w-]{6,15})").find(text) ?: return text.trim()
        return m.groupValues[1]
    }
    fun playlistOf(text: String): String = Regex("list=([\\w-]{6,40})").find(text)?.groupValues?.get(1) ?: ""

    /** إضافة درس من رابط يوتيوب مفرد → «وارد». */
    suspend fun importVideoLink(text: String): String = withContext(Dispatchers.IO) {
        val yt = ytOf(text); if (yt.isEmpty()) return@withContext ""
        val d = File(root, "وارد/$yt"); d.mkdirs()
        File(d, "yt.txt").writeText(yt)
        yt
    }

    /** إضافة سلسلة من رابط قائمة يوتيوب — يجلب صفحة القائمة ويستخرج معرفات الدروس. */
    suspend fun importPlaylist(text: String): Int = withContext(Dispatchers.IO) {
        val pl = playlistOf(text); if (pl.isEmpty()) return@withContext 0
        val sd = File(root, "قائمة-$pl"); sd.mkdirs()
        File(sd, "series.json").writeText("{\"title\":\"قائمة يوتيوب $pl\",\"science\":\"local\"}")
        runCatching {
            val html = java.net.URL("https://www.youtube.com/playlist?list=$pl").readText()
            val ids = Regex("\"videoId\":\"([\\w-]{11})\"").findAll(html).map { it.groupValues[1] }.toList().distinct()
            ids.forEachIndexed { i, yt ->
                val d = File(sd, "%03d".format(i + 1)); d.mkdirs()
                File(d, "yt.txt").writeText(yt)
            }
            ids.size
        }.getOrDefault(0)
    }
}
