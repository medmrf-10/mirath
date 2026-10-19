package com.irth.mirath.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.irth.mirath.store.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/**
 * فهرس نصوص المكتبة — ملف لكل درس في files/tindex: سطر رأس (T موقوت | A تقريبي) ثم «ثانية\tجملة».
 * يمتلئ تلقائياً عند فتح أي درس أو تنزيل أي سلسلة، أو على دفعات ذكية بـbuildAll في الخلفية.
 */
object TextIndex {
    private var dir: File? = null
    val count = mutableStateOf(0)
    val running = mutableStateOf(false)
    val done = mutableStateOf(0)
    val total = mutableStateOf(0)
    val rev = mutableStateOf(0)
    private var job: Job? = null

    data class Hit(val id: String, val s: Double, val approx: Boolean, val snippet: String, val at: Int, val len: Int)
    data class Group(val id: String, val hits: List<Hit>, val total: Int)

    fun init(ctx: Context) {
        if (dir != null) return
        dir = File(ctx.filesDir, "tindex").apply { mkdirs() }
        count.value = dir?.list()?.size ?: 0
    }

    private fun fileOf(id: String): File? = dir?.let { File(it, URLEncoder.encode(id, "UTF-8") + ".tsv") }
    private fun idOf(f: File): String = URLDecoder.decode(f.name.removeSuffix(".tsv"), "UTF-8")

    fun has(id: String): Boolean = fileOf(id)?.exists() == true
    fun timed(id: String): Boolean = fileOf(id)?.takeIf { it.exists() }?.bufferedReader()?.use { it.readLine() } == "T"

    fun sizeMb(): Double = (dir?.listFiles()?.sumOf { it.length() } ?: 0L) / 1048576.0

    private val diac = Regex("[\\u064B-\\u065F\\u0670\\u0640\\u06D6-\\u06ED\\u200B-\\u200F\\uFEFF]")

    /** للعرض: بلا تشكيل/تطويل ومسافات موحدة. */
    fun clean(s: String): String = diac.replace(s, "").replace(Regex("\\s+"), " ").trim()

    /** طيّ حرفي يحفظ الطول: أ/إ/آ/ٱ→ا، ى/ئ→ي، ة→ه، ؤ→و، والأرقام الهندية→العربية — لتطابق مؤشرات الإبراز مع نص العرض. */
    fun fold(s: String): String {
        val c = CharArray(s.length)
        for (i in s.indices) c[i] = when (val ch = s[i]) {
            'أ', 'إ', 'آ', 'ٱ' -> 'ا'
            'ى', 'ئ' -> 'ي'
            'ة' -> 'ه'
            'ؤ' -> 'و'
            in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
            else -> ch.lowercaseChar()
        }
        return String(c)
    }

    private fun write(id: String, kind: String, lines: List<Pair<Double, String>>) {
        val f = fileOf(id) ?: return
        val existed = f.exists()
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.bufferedWriter().use { w ->
                w.write(kind); w.write("\n")
                lines.forEach { (s, t) ->
                    if (t.isNotBlank()) {
                        w.write("%.2f".format(java.util.Locale.US, s))
                        w.write("\t")
                        w.write(t)
                        w.write("\n")
                    }
                }
            }
            tmp.renameTo(f)
        }
        if (!existed) count.value = count.value + 1
        rev.value++
    }

    private fun alignLines(ws: List<AWord>) = buildPhrases(ws).map { ph -> ph.first().s to clean(ph.joinToString(" ") { it.w }) }

    /** نص بلا محاذاة: مقاطع جُمَلية بوقت تقريبي = موضع الحرف × المدة. */
    private fun textLines(t: String, duration: Int): List<Pair<Double, String>> {
        val out = mutableListOf<Pair<Double, String>>()
        val n = t.length.coerceAtLeast(1)
        var start = 0
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            val ch = t[i]
            sb.append(ch)
            val brk = ch == '\n' || ((ch == '.' || ch == '؟' || ch == '!' || ch == '،' || ch == '؛') && sb.length >= 60) || sb.length >= 160
            if (brk) {
                val c = clean(sb.toString())
                if (c.isNotEmpty()) out += (if (duration > 0) start.toDouble() / n * duration else -1.0) to c
                sb.clear(); start = i + 1
            }
            i++
        }
        val c = clean(sb.toString())
        if (c.isNotEmpty()) out += (if (duration > 0) start.toDouble() / n * duration else -1.0) to c
        return out
    }

    fun putAlign(id: String, ws: List<AWord>) {
        if (ws.isEmpty() || timed(id)) return
        Haw.bg.launch { write(id, "T", alignLines(ws)) }
    }

    fun putText(id: String, t: String, duration: Int) {
        if (t.isBlank() || has(id)) return
        Haw.bg.launch { write(id, "A", textLines(t, duration)) }
    }

    /** فهرسة ذكية على دفعات (الأولوية للمحلي والدروس المفتوحة والمحاذاة) لتجنب إغراق الشبكة بـ 6,589 طلباً دفعة واحدة. */
    fun buildAll(maxBatch: Int = 250) {
        if (job?.isActive == true) return
        running.value = true
        job = Haw.bg.launch {
            val prioritySeries = (Prefs.recent.value + Prefs.favs.value)
                .mapNotNull { Repo.seriesOfVideo(it)?.id ?: Repo.seriesOf(it)?.id }
                .distinct()
            prioritySeries.forEach { runCatching { Haw.ensure(it) } }

            val candidates = Repo.videos.orEmpty().filter { v ->
                val a = v.has.contains("align"); val t = v.has.contains("transcript")
                (a && !timed(v.id)) || (!a && t && !has(v.id))
            }.sortedWith(
                compareByDescending<Video> { it.id.startsWith("L:") }
                    .thenByDescending { Repo.seriesOfVideo(it.id)?.id in prioritySeries }
                    .thenByDescending { it.has.contains("align") }
            ).take(maxBatch)

            // تأكد من تحميل فهارس السلاسل للدروس المختارة في هذه الدفعة
            candidates.mapNotNull { Repo.seriesOfVideo(it.id)?.id }.distinct().forEach {
                runCatching { Haw.ensure(it) }
            }

            val todo = candidates.mapNotNull { Repo.video(it.id) }
            total.value = todo.size; done.value = 0
            val n = AtomicInteger(0)
            val sem = Semaphore(4)
            todo.map { v ->
                launch {
                    sem.withPermit {
                        runCatching {
                            var ok = false
                            if (v.has.contains("align")) {
                                val ws = Repo.loadAlign(v.id, cache = false)
                                if (ws.isNotEmpty()) { write(v.id, "T", alignLines(ws)); ok = true }
                            }
                            if (!ok && v.has.contains("transcript") && !has(v.id)) {
                                val t = Repo.loadTranscript(v.id, cache = false)
                                if (t.isNotBlank()) write(v.id, "A", textLines(t, v.duration))
                            }
                        }
                        done.value = n.incrementAndGet()
                    }
                }
            }.joinAll()
            running.value = false
        }.also { it.invokeOnCompletion { running.value = false } }
    }

    fun stop() { job?.cancel(); running.value = false }

    fun clear() {
        stop()
        dir?.listFiles()?.forEach { it.delete() }
        count.value = 0; rev.value++
    }

    /** بحث تدفقي: يطابق المطوي بالمطوي، ويلتقط العبارات العابرة لحد جملتين. نتائج مجمّعة بالدرس. */
    suspend fun search(q: String, maxGroups: Int = 60, perGroup: Int = 30): List<Group> = withContext(Dispatchers.IO) {
        val fq = fold(clean(q))
        if (fq.length < 2) return@withContext emptyList()
        val files = dir?.listFiles()?.filter { it.name.endsWith(".tsv") } ?: return@withContext emptyList()
        val groups = mutableListOf<Group>()
        for (f in files) {
            coroutineContext.ensureActive()
            val lines = runCatching { f.readLines() }.getOrNull() ?: continue
            if (lines.size < 2) continue
            val approx = lines[0] != "T"
            val ss = DoubleArray(lines.size - 1); val ts = arrayOfNulls<String>(lines.size - 1)
            for (i in 1 until lines.size) {
                val l = lines[i]; val tab = l.indexOf('\t'); if (tab < 0) { ts[i - 1] = ""; continue }
                ss[i - 1] = l.substring(0, tab).toDoubleOrNull() ?: -1.0; ts[i - 1] = l.substring(tab + 1)
            }
            val hits = mutableListOf<Hit>(); var tot = 0
            for (i in ts.indices) {
                val cur = ts[i] ?: continue
                val next = ts.getOrNull(i + 1).orEmpty()
                val comb = if (next.isEmpty()) cur else "$cur $next"
                val fc = fold(comb)
                var at = fc.indexOf(fq)
                while (at in 0 until cur.length) {
                    tot++
                    if (hits.size < perGroup) {
                        val from = (at - 50).coerceAtLeast(0)
                        val to = (at + fq.length + 60).coerceAtMost(comb.length)
                        val sn = (if (from > 0) "…" else "") + comb.substring(from, to) + (if (to < comb.length) "…" else "")
                        hits += Hit(idOf(f), ss[i], approx, sn, at - from + (if (from > 0) 1 else 0), fq.length)
                    }
                    at = fc.indexOf(fq, at + fq.length)
                }
            }
            if (tot > 0) groups += Group(idOf(f), hits, tot)
        }
        groups.sortedWith(
            compareByDescending<Group> { g -> Repo.video(g.id)?.title?.let { fold(clean(it)).contains(fq) } == true }
                .thenByDescending { it.total }
        ).take(maxGroups)
    }

    fun eligible(): Int = Repo.videos.orEmpty().count { it.has.contains("align") || it.has.contains("transcript") }
}
