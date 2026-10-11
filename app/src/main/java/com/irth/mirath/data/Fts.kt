package com.irth.mirath.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

/**
 * فهرس نصي كامل للمكتبة على الهاتف — كل جملة بثانيتها.
 * ملف لكل درس: سطر رأس «A|T\t<id>» ثم «ثانية\tنص». A = محاذاة دقيقة، T = تفريغ بتوقيت تقديري.
 * يعمل للكتالوج وهوشات والمحلي معاً لأنه يقرأ عبر Repo.
 */
object Fts {
    private var dir: File? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    val count = mutableStateOf(0)
    val busy = mutableStateOf(false)
    val done = mutableStateOf(0)
    val total = mutableStateOf(0)

    fun init(ctx: Context) {
        if (dir != null) return
        dir = File(ctx.filesDir, "fts").apply { mkdirs() }
        count.value = dir?.list()?.size ?: 0
    }

    private fun file(id: String): File? = dir?.let { File(it, URLEncoder.encode(id, "UTF-8") + ".txt") }
    fun has(id: String): Boolean = file(id)?.exists() == true

    private val marks = Regex("[\u064B-\u065F\u0670\u06D6-\u06ED\u0640]")

    /** حذف التشكيل والتطويل — يبقى النص مقروءاً للعرض. */
    fun clean(s: String): String = s.replace(marks, "").replace('\t', ' ').replace('\n', ' ')

    /** طيّ حرف بحرف (طول ثابت) — يجعل مواضع التظليل في الطيّ هي مواضعها في النص المعروض. */
    private fun fold(s: String): String {
        val a = CharArray(s.length)
        for (i in s.indices) {
            a[i] = when (val c = s[i]) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ى' -> 'ي'
                'ة' -> 'ه'
                'ؤ' -> 'و'
                'ئ' -> 'ي'
                else -> c
            }
        }
        return String(a)
    }

    fun normQuery(q: String): String = fold(clean(q).trim().replace(Regex("\\s+"), " "))

    private fun write(id: String, kind: Char, rows: List<Pair<Int, String>>) {
        val f = file(id) ?: return
        if (rows.isEmpty()) return
        val isNew = !f.exists()
        runCatching {
            val tmp = File(f.path + ".tmp")
            tmp.bufferedWriter().use { w ->
                w.write("$kind\t$id\n")
                rows.forEach { (s, t) -> if (t.isNotBlank()) { w.write("$s\t$t\n") } }
            }
            tmp.renameTo(f)
            if (isNew) count.value++
        }
    }

    fun putWords(id: String, ws: List<AWord>) {
        if (ws.isEmpty() || has(id)) return
        write(id, 'A', buildPhrases(ws).map { ph -> ph.first().s.toInt() to clean(ph.joinToString(" ") { it.w }) })
    }

    /** تفريغ بلا محاذاة: جُمل قصيرة بتوقيت تناسبي من طول النص — يُعرض بعلامة ≈. */
    fun putText(id: String, text: String, duration: Int) {
        if (text.isBlank() || has(id)) return
        val t = clean(text.replace('\n', ' ')).replace(Regex(" +"), " ")
        val parts = mutableListOf<Pair<Int, String>>()
        var i = 0
        while (i < t.length) {
            var j = (i + 110).coerceAtMost(t.length)
            if (j < t.length) {
                val stop = t.lastIndexOfAny(charArrayOf('.', '،', '؟', '!', '؛', ' '), j)
                if (stop > i + 40) j = stop + 1
            }
            val sec = if (duration > 0) (i.toLong() * duration / t.length).toInt() else -1
            parts += sec to t.substring(i, j).trim()
            i = j
        }
        write(id, 'T', parts)
    }

    data class Hit(val id: String, val sec: Int, val text: String, val at: Int, val len: Int, val approx: Boolean)
    data class Group(val id: String, val hits: List<Hit>)

    /** بحث في كل الفهرس: نتائج مجمّعة بالدرس، الأكثر تطابقاً أولاً. */
    suspend fun search(q: String, maxPerLesson: Int = 4, maxLessons: Int = 60): List<Group> = withContext(Dispatchers.Default) {
        val nq = normQuery(q)
        if (nq.length < 2) return@withContext emptyList()
        val files = dir?.listFiles()?.filter { it.name.endsWith(".txt") } ?: return@withContext emptyList()
        val out = mutableListOf<Pair<Group, Int>>()
        for (f in files) {
            ensureActive()
            val lines = runCatching { f.readLines() }.getOrNull() ?: continue
            if (lines.isEmpty()) continue
            val head = lines[0]
            val approx = head.startsWith("T")
            val id = head.substringAfter('\t')
            val rows = lines.drop(1).mapNotNull { l ->
                val k = l.indexOf('\t'); if (k <= 0) null else (l.substring(0, k).toIntOrNull() ?: -1) to l.substring(k + 1)
            }
            val hits = mutableListOf<Hit>(); var n = 0
            for (r in rows.indices) {
                val cur = rows[r].second
                val joined = if (r + 1 < rows.size) cur + " " + rows[r + 1].second else cur
                val at = fold(joined).indexOf(nq)
                if (at < 0 || at > cur.length) continue
                n++
                if (hits.size < maxPerLesson) hits += Hit(id, rows[r].first, joined, at, nq.length, approx)
            }
            if (n > 0) out += Group(id, hits) to n
        }
        out.sortedByDescending { it.second }.take(maxLessons).map { it.first }
    }

    /** عدد التطابقات الكلي لدرس — للعرض «+N». */
    fun countIn(id: String, q: String): Int {
        val nq = normQuery(q); val f = file(id) ?: return 0
        return runCatching { f.readLines().drop(1).count { fold(it.substringAfter('\t')).contains(nq) } }.getOrDefault(0)
    }

    /** فهرسة دفعة دروس في الخلفية — تستمر بعد إغلاق الشاشة، قابلة للإلغاء. */
    fun indexAll(ids: List<String>) {
        if (busy.value) return
        val todo = ids.distinct().filter { !has(it) }
        total.value = todo.size; done.value = 0
        if (todo.isEmpty()) return
        busy.value = true
        job = scope.launch {
            val gate = Semaphore(4)
            todo.map { id ->
                async {
                    gate.withPermit {
                        ensureActive()
                        runCatching { indexOne(id) }
                        done.value++
                    }
                }
            }.awaitAll()
        }.also { it.invokeOnCompletion { busy.value = false } }
    }

    /** كتابة الفهرس خارج الخيط الرئيسي — للاستدعاء من مسارات فتح الدرس. */
    fun later(b: () -> Unit) { scope.launch { runCatching { b() } } }

    fun cancel() { job?.cancel(); busy.value = false }

    private suspend fun indexOne(id: String) {
        if (has(id)) return
        val v = Repo.video(id)
        val ws = Repo.alignNoCache(id)
        if (ws.isNotEmpty()) { putWords(id, ws); return }
        val t = Repo.transcriptNoCache(id)
        if (t.isNotBlank()) putText(id, t, v?.duration ?: 0)
    }
}
