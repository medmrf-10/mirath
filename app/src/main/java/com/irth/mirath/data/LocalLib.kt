package com.irth.mirath.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * مكتبة المستخدم المحلية — التطبيق منصة عامة: المستخدم ينشئ سلاسله بنفسه
 * (أيقونة + مجلدات متداخلة + دروس من يوتيوب أو ملفات)، أو يستورد حزم الوكيل.
 *
 * البنية: files/imports/[<مجلدات متداخلة>/]<سلسلة>/<درس>/{transcript.txt,align.json,summary.json,questions.json,media.mp3|mp4,yt.txt}
 * وملف series.json موسّع: {"title","science","kind","icon","order":[ids],"lessons":{"<id>":{"title","duration"}}}
 * سلسلة = مجلد فيه series.json، أو مجلد أول-مستوى فيه مجلدات دروس (الحزم القديمة).
 */
object LocalLib {
    private var root: File? = null

    fun init(ctx: Context) {
        if (root == null) root = File(ctx.filesDir, "imports").apply { mkdirs() }
    }

    /** سلسلة: فيها series.json — أو مجلد أول-مستوى فيه مجلدات فرعية (حزم قديمة). */
    private fun seriesDirs(): List<File> {
        val r = root ?: return emptyList()
        val out = mutableListOf<File>()
        fun scan(d: File, depth: Int) {
            val kids = d.listFiles()?.filter { it.isDirectory } ?: emptyList()
            if (File(d, "series.json").exists() || (depth == 1 && kids.isNotEmpty())) { out += d; return }
            if (depth < 5) kids.forEach { scan(it, depth + 1) }
        }
        r.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { scan(it, 1) }
        return out.sortedBy { it.relativeTo(r).path }
    }

    private fun slugOf(d: File): String = d.relativeTo(root!!).path
    private fun dirOf(slug: String): File = File(root, slug)
    private fun folderOf(d: File): String = d.relativeTo(root!!).parent ?: ""

    private fun metaFile(sd: File) = File(sd, "series.json")
    private fun metaOf(sd: File): UMeta = runCatching {
        Api.json.decodeFromString<UMeta>(metaFile(sd).readText())
    }.getOrDefault(UMeta(title = sd.name))

    private fun writeMeta(sd: File, m: UMeta) {
        metaFile(sd).writeText(Api.json.encodeToString(m))
    }

    /** سلاسل المستخدم مدمجة مع الكتالوج — id على شكل «L:<مسار نسبي>». */
    fun localSeries(): List<Series> = seriesDirs().map { d ->
        val m = metaOf(d)
        val ordered = orderLessons(d, m)
        Series(
            id = "L:${slugOf(d)}", title = m.title.ifEmpty { d.name },
            science = m.science.ifEmpty { "local" }, kind = m.kind,
            items = ordered.map { "L:${slugOf(d)}/${it.name}" },
            icon = m.icon, folder = folderOf(d),
        )
    }

    /** دروس السلسلة بترتيب meta.order ثم بقية المجلدات أبجدياً. */
    private fun orderLessons(sd: File, m: UMeta): List<File> {
        val dirs = sd.listFiles()?.filter { it.isDirectory }?.associateBy { it.name } ?: emptyMap()
        val out = m.order.mapNotNull { dirs[it] }.toMutableList()
        dirs.keys.sorted().filter { k -> out.none { it.name == k } }.forEach { out += dirs[it]!! }
        return out
    }

    private fun lessonMetaOf(m: UMeta, lid: String): Pair<String, Int> {
        val l = m.lessons[lid]
        return (if (l != null && l.title.isNotEmpty()) l.title else lid) to (l?.duration ?: 0)
    }

    fun localVideos(): List<Video> = seriesDirs().flatMap { sd ->
        val m = metaOf(sd)
        orderLessons(sd, m).map { ld ->
            val (title, dur) = lessonMetaOf(m, ld.name)
            val has = mutableListOf<String>()
            if (File(ld, "transcript.txt").exists()) has += "transcript"
            if (File(ld, "align.json").exists()) has += "align"
            if (File(ld, "summary.json").exists()) has += "summary"
            if (File(ld, "questions.json").exists()) has += "questions"
            if (mediaFile(slugOf(sd), ld.name) != null) has += "media"
            val yt = File(ld, "yt.txt").takeIf { it.exists() }?.readText()?.trim()
                ?: ytOf(File(ld, "link.txt").takeIf { it.exists() }?.readText()?.trim() ?: "")
            if (yt.isNotEmpty()) has += "yt"
            Video(id = "L:${slugOf(sd)}/${ld.name}", title = title, duration = dur, has = has, yt = yt)
        }
    }

    fun localScience(): Science? =
        if (localSeries().isNotEmpty()) Science("local", "مكتبتي") else null

    private fun ld(slug: String, lid: String): File = File(root, "$slug/$lid")

    private fun file(id: String, name: String): File? {
        // دروس الكتالوج تقرأ ملفاتها الملحقة محلياً من .link/<vid>/
        if (!id.startsWith("L:")) return File(linkDir(id), name).takeIf { it.exists() }
        val raw = id.removePrefix("L:")
        val slug = raw.substringBeforeLast("/", "") // مسار السلسلة وقد يكون متداخلاً «مجلد/سلسلة»
        val lid = raw.substringAfterLast("/", "")
        if (slug.isEmpty() || lid.isEmpty()) return null
        return File(ld(slug, lid), name).takeIf { it.exists() }
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

    /** وسائط مرتبطة بدروس الكتالوج البعيد: imports/.link/<videoId>/media.* */
    private fun linkDir(vid: String) = File(root, ".link/$vid")

    fun mediaFor(id: String): File? {
        if (!id.startsWith("L:"))
            return linkDir(id).listFiles()?.firstOrNull { it.name.startsWith("media.") }
        val raw = id.removePrefix("L:")
        val slug = raw.substringBeforeLast("/", "")
        val lid = raw.substringAfterLast("/", "")
        return if (slug.isEmpty() || lid.isEmpty()) null else mediaFile(slug, lid)
    }

    /** هل للسلسلة وسائط محلية تسمح بوضع «محلي»؟ items لازمة لسلاسل الكتالوج. */
    fun seriesHasMedia(seriesId: String, items: List<String> = emptyList()): Boolean {
        if (!seriesId.startsWith("L:"))
            return items.any { linkDir(it).listFiles()?.any { f -> f.name.startsWith("media.") } == true }
        val sd = dirOf(seriesId.removePrefix("L:"))
        return sd.listFiles()?.filter { it.isDirectory }?.any { c ->
            c.listFiles()?.any { it.name.startsWith("media.") } == true
        } == true
    }

    private fun isMediaName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in listOf("mp3", "m4a", "wav", "ogg", "mp4", "webm", "mkv")

    private fun naturalKey(name: String): String =
        name.lowercase().replace(Regex("\\d+")) { it.value.padStart(8, '0') }

    /**
     * الأوفلاين بالعدد (بأمر محمد): اختيار مجلد فيه وسائط مساوية عدد دروس السلسلة ← ربط آلي بالترتيب.
     * يعيد: n مرتبطة | 0 لا وسائط | -1 عدد غير مطابق.
     */
    suspend fun autoLinkFolder(ctx: Context, seriesId: String, items: List<String>, uri: Uri): Int = withContext(Dispatchers.IO) {
        val doc = DocumentFile.fromTreeUri(ctx, uri) ?: return@withContext 0
        val media = mutableListOf<DocumentFile>()
        fun walk(d: DocumentFile) {
            d.listFiles().forEach { if (it.isDirectory) walk(it) else if (isMediaName(it.name ?: "")) media += it }
        }
        walk(doc)
        if (media.isEmpty()) return@withContext 0
        media.sortBy { naturalKey(it.name ?: "") }
        if (media.size != items.size) return@withContext -1
        var n = 0
        items.forEachIndexed { i, vid ->
            val f = media[i]
            val ext = (f.name ?: "media.mp3").substringAfterLast('.')
            val out = if (vid.startsWith("L:")) File(root, "${vid.removePrefix("L:")}/media.$ext")
                      else File(linkDir(vid), "media.$ext")
            out.parentFile?.mkdirs()
            runCatching {
                ctx.contentResolver.openInputStream(f.uri)?.use { ins -> out.outputStream().use { ins.copyTo(it) } }
                n++
            }
        }
        n
    }

    fun totalBytes(): Long = root?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0

    // ===== إنشاء وتحرير سلاسل المستخدم =====

    private fun slugify(t: String): String =
        t.trim().replace(Regex("[\\\\/:\"*?<>|]"), "-").replace(Regex("\\s+"), " ").take(60).ifEmpty { "سلسلة" }

    private fun uniqueSlug(folder: String, want: String): String {
        var s = want; var i = 2
        while (File(root, "${folder}/$s".trim('/')).exists()) s = "$want-${i++}"
        return s
    }

    /** إنشاء سلسلة فارغة — يعيد id «L:<مسار>»؛ folder مسار نسبي متداخل اختياري «فقه/ابن-باز». */
    fun createSeries(title: String, icon: String, folder: String): String {
        val f = folder.trim().trim('/').split("/").filter { it.isNotBlank() }.joinToString("/") { slugify(it) }
        val slug = uniqueSlug(f, slugify(title))
        val sd = File(root, if (f.isEmpty()) slug else "$f/$slug"); sd.mkdirs()
        writeMeta(sd, UMeta(title = title.trim().ifEmpty { slug }, icon = icon))
        return "L:${slugOf(sd)}"
    }

    fun seriesMeta(seriesId: String): UMeta = metaOf(dirOf(seriesId.removePrefix("L:")))

    fun setSeriesInfo(seriesId: String, title: String, icon: String) {
        val sd = dirOf(seriesId.removePrefix("L:"))
        writeMeta(sd, metaOf(sd).copy(title = title.trim().ifEmpty { sd.name }, icon = icon))
    }

    /** استبدال قائمة الدروس كاملة (عناوين+ترتيب) — dir names ثابتة فلا يُفقد التقدّم. */
    fun setLessons(seriesId: String, items: List<Pair<String, String>>) {
        val sd = dirOf(seriesId.removePrefix("L:"))
        val m = metaOf(sd)
        val lm = items.associate { (lid, t) -> lid to (m.lessons[lid]?.copy(title = t) ?: ULesson(title = t)) }
        writeMeta(sd, m.copy(order = items.map { it.first }, lessons = lm))
    }

    /** قائمة الدروس الحالية للتحرير: (lid, title). */
    fun lessonsOf(seriesId: String): List<Pair<String, String>> {
        val sd = dirOf(seriesId.removePrefix("L:"))
        val m = metaOf(sd)
        return orderLessons(sd, m).map { it.name to lessonMetaOf(m, it.name).first }
    }

    /** إضافة درس لسلسلة مستخدم — يعيد لقب الدرس (dir name). */
    fun addLesson(seriesId: String, title: String, yt: String): String {
        val sd = dirOf(seriesId.removePrefix("L:"))
        val m = metaOf(sd)
        val lid = uniqueLessonLid(sd)
        File(sd, lid).mkdirs()
        if (yt.isNotEmpty()) File(sd, "$lid/yt.txt").writeText(ytOf(yt))
        writeMeta(sd, m.copy(
            order = m.order + lid,
            lessons = m.lessons + (lid to ULesson(title = title.trim().ifEmpty { lid })),
        ))
        return lid
    }

    private fun uniqueLessonLid(sd: File): String {
        var i = sd.listFiles()?.count { it.isDirectory }?.plus(1) ?: 1
        while (File(sd, "%03d".format(i)).exists()) i++
        return "%03d".format(i)
    }

    fun deleteSeries(seriesId: String) { dirOf(seriesId.removePrefix("L:")).deleteRecursively() }

    fun removeLesson(seriesId: String, lid: String) {
        val sd = dirOf(seriesId.removePrefix("L:"))
        File(sd, lid).deleteRecursively()
        val m = metaOf(sd)
        writeMeta(sd, m.copy(order = m.order - lid, lessons = m.lessons - lid))
    }

    // ===== استيراد =====

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
            if (name.lowercase().endsWith(".zip")) {
                // حزمة مضغوطة (نسخة احتياطية أو سلسلة مشاركة) — استخرج بالبنية كما هي
                runCatching {
                    ctx.contentResolver.openInputStream(u)?.use { ins ->
                        java.util.zip.ZipInputStream(ins.buffered()).use { z ->
                            var e = z.nextEntry
                            while (e != null) {
                                val rel = e.name.removePrefix("imports/").trimStart('/')
                                if (!e.isDirectory && rel.isNotBlank() && !rel.contains("..")) {
                                    val out = File(root, rel)
                                    out.parentFile?.mkdirs()
                                    out.outputStream().use { z.copyTo(it) }
                                    n++
                                }
                                z.closeEntry(); e = z.nextEntry
                            }
                        }
                    }
                }
                return@forEach
            }
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

    /** إرفاق ملف واحد بدرس محدد في سلسلة مستخدم (تصنيف من الاسم). */
    suspend fun attachTo(ctx: Context, seriesId: String, lid: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val d = DocumentFile.fromSingleUri(ctx, uri) ?: return@withContext false
        val (_, canon) = classify(d.name ?: return@withContext false)
        val out = File(ld(seriesId.removePrefix("L:"), lid), canon)
        out.parentFile?.mkdirs()
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
        }.isSuccess
    }

    /** إرفاق ملف بدرس من الكتالوج — يُحفظ ظلّاً في .link/<vid>/ ويحجب/يكمل بيانات الشبكة. */
    suspend fun attachToCatalog(ctx: Context, vid: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val d = DocumentFile.fromSingleUri(ctx, uri) ?: return@withContext false
        val (_, canon) = classify(d.name ?: return@withContext false)
        if (canon == "yt.txt") return@withContext false
        val out = File(linkDir(vid), canon)
        out.parentFile?.mkdirs()
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
        }.isSuccess
    }

    /** أنواع الملفات الملحقة محلياً بدرس كتالوج (.link/<vid>/) — تُدمج في أعلام has. */
    fun shadowKinds(vid: String): List<String> =
        (linkDir(vid).listFiles() ?: emptyArray()).mapNotNull { f ->
            when {
                f.name == "transcript.txt" -> "transcript"
                f.name == "align.json" -> "align"
                f.name == "summary.json" -> "summary"
                f.name == "questions.json" -> "questions"
                f.name.startsWith("media.") -> "media"
                else -> null
            }
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

    /**
     * جلب قائمة يوتيوب: (عنوان القائمة، [(ytId, عنوان الحلقة) بالترتيب]).
     * يُستخدم في شاشة الإنشاء — المستخدم يعدّل العناوين والترتيب قبل الحفظ.
     */
    suspend fun fetchPlaylist(text: String): Pair<String, List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        val pl = playlistOf(text); if (pl.isEmpty()) return@withContext ("" to emptyList())
        runCatching {
            val html = java.net.URL("https://www.youtube.com/playlist?list=$pl").readText()
            val plTitle = Regex("<title>(.+?)</title>").find(html)?.groupValues?.get(1)
                ?.replace(Regex(" - YouTube$"), "")?.trim() ?: ""
            val pat = Regex("\"videoId\":\"([\\w-]{11})\".{0,500}?\"title\":\\{\"runs\":\\[\\{\"text\":\"(.+?)\"", RegexOption.DOT_MATCHES_ALL)
            val items = pat.findAll(html).map { it.groupValues[1] to unesc(it.groupValues[2]) }
                .distinctBy { it.first }.toList()
            plTitle to items
        }.getOrDefault("" to emptyList())
    }

    private fun unesc(s: String): String = s
        .replace("\\u0026", "&").replace("\\u003c", "<").replace("\\u003e", ">")
        .replace("\\u0027", "'").replace("\\\"", "\"").replace("\\\\", "\\")
        .let { t -> Regex("\\\\u([0-9a-fA-F]{4})").replace(t) { m -> m.groupValues[1].toInt(16).toChar().toString() } }

    /** حفظ سلسلة مستوردة من قائمة: (id, title) مرتبة ومعنونة كما عدّلها المستخدم. */
    fun createFromPlaylist(title: String, icon: String, folder: String, items: List<Pair<String, String>>): String {
        val sid = createSeries(title, icon, folder)
        val sd = dirOf(sid.removePrefix("L:"))
        val lids = mutableListOf<String>()
        items.forEachIndexed { i, (yt, t) ->
            val lid = "%03d".format(i + 1)
            File(sd, lid).mkdirs()
            File(sd, "$lid/yt.txt").writeText(yt)
            lids += lid
        }
        val m = metaOf(sd)
        writeMeta(sd, m.copy(
            order = lids,
            lessons = items.mapIndexed { i, (_, t) -> lids[i] to ULesson(title = t.ifEmpty { "حلقة ${i + 1}" }) }.toMap(),
        ))
        return sid
    }
}

@Serializable
data class UMeta(
    val title: String = "",
    val science: String = "local",
    val kind: String = "",
    val icon: String = "book",
    val order: List<String> = emptyList(),
    val lessons: Map<String, ULesson> = emptyMap(),
)

@Serializable
data class ULesson(val title: String = "", val duration: Int = 0)
