package com.irth.mirath.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class Rows<T>(val v: Int = 0, val rows: List<T> = emptyList())

@Serializable
data class Science(
    val id: String = "",
    val name: String = "",
    val seriesCount: Int = 0,
    val lessonsCount: Int = 0,
    val alignedCount: Int = 0,
    val photo: String = "",
)

@Serializable
data class Series(
    val id: String = "",
    val title: String = "",
    val science: String = "",
    val kind: String = "",
    val items: List<String> = emptyList(),
    val icon: String = "",
    val folder: String = "",
    val category: String = "",
    val alignedCount: Int = 0,
    val totalDur: Int = 0,
) {
    val lessonsCount: Int get() = items.size
    val duration: Int get() = totalDur
}

@Serializable
data class Person(
    val id: String = "",
    val name: String = "",
    val short: String = "",
    val photo: String = "",
)

@Serializable
data class Work(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val science: String = "",
    val kind: String = "",
)

@Serializable
data class Video(
    val id: String = "",
    val title: String = "",
    val yt: String = "",
    val speakers: List<String> = emptyList(),
    val duration: Int = 0,
    val has: List<String> = emptyList(),
    val work: String = "",
    val ytTitle: String = "",
    val alignCov: Double = 0.0,
) {
    val alignCoverage: Double get() = alignCov
}

@Serializable
data class Summary(
    val summary: String = "",
    val points: List<String> = emptyList(),
)

@Serializable
data class QA(val q: String = "", val a: String = "")

@Serializable
data class Questions(val questions: List<QA> = emptyList())

/** كلمة محاذاة: الكلمة وبدايتها ونهايتها بالثواني، مع علم interp للكلمات التقديرية. */
data class AWord(val w: String, val s: Double, val e: Double, val interp: Boolean = false)

private data class RawW(val t: String, val s: Double?, val e: Double?, val flag: String = "a")

private fun repairWords(raw: List<RawW>, totalDur: Double = 0.0): List<AWord> {
    if (raw.isEmpty()) return emptyList()
    val out = raw.map {
        AWord(it.t, it.s ?: -1.0, it.e ?: -1.0, interp = (it.s == null || it.e == null || it.flag != "a"))
    }.toMutableList()
    var i = 0
    while (i < out.size) {
        if (out[i].s >= 0.0 && out[i].e >= out[i].s) { i++; continue }
        val a = i
        while (i < out.size && (out[i].s < 0.0 || out[i].e < out[i].s)) i++
        val t0 = if (a > 0) out[a - 1].e.coerceAtLeast(0.0) else 0.0
        val t1 = if (i < out.size && out[i].s >= t0) out[i].s else totalDur.takeIf { it > t0 } ?: (t0 + (i - a) * 0.38)
        val span = (t1 - t0).coerceAtLeast((i - a) * 0.05)
        val chars = (a until i).sumOf { out[it].w.length + 1 }.toDouble().coerceAtLeast(1.0)
        var c = t0
        for (k in a until i) {
            val d = span * (out[k].w.length + 1) / chars
            out[k] = AWord(out[k].w, c, c + d, interp = true)
            c += d
        }
    }
    for (k in 1 until out.size) {
        val prev = out[k - 1]
        val cur = out[k]
        val ns = maxOf(cur.s, prev.s)
        val ne = maxOf(cur.e, ns + 0.04)
        if (ns != cur.s || ne != cur.e) out[k] = cur.copy(s = ns, e = ne)
    }
    return out
}

/**
 * قراءة موحدة ومرنة لجميع صيغ المحاذاة:
 * 1) صيغة ytb الحديثة: {"dur": ..., "cov": ..., "x": [["كلمة", 0.52, 0.88, "a"], ...]}
 * 2) صيغة hawshat السابقة: {"w": [["كلمة", 0.52, 0.88], ...]}
 * 3) صيغة irth القديمة: {"words": "...", "start": [...], "dur": [...]}
 */
fun parseAlign(el: JsonElement): List<AWord> {
    val obj = el as? JsonObject ?: return emptyList()
    val dur = (obj["dur"] as? JsonPrimitive)?.doubleOrNull ?: 0.0

    // 1. صيغة x في مستودع ytb الموحد أو صيغة w في hawshat
    val arrX = (obj["x"] as? JsonArray) ?: (obj["w"] as? JsonArray)
    if (arrX != null) {
        val raw = arrX.mapNotNull { item ->
            val a = item as? JsonArray ?: return@mapNotNull null
            if (a.isEmpty()) return@mapNotNull null
            val word = (a[0] as? JsonPrimitive)?.contentOrNull?.trim() ?: return@mapNotNull null
            if (word.isEmpty()) return@mapNotNull null
            val s = (a.getOrNull(1) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull
            val e = (a.getOrNull(2) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull
            val flag = (a.getOrNull(3) as? JsonPrimitive)?.contentOrNull ?: "a"
            RawW(word, s, e, flag)
        }
        return repairWords(raw, dur)
    }

    // 2. صيغة words + start + dur القديمة
    val words = (obj["words"] as? JsonPrimitive)?.contentOrNull?.split(' ')?.filter { it.isNotBlank() } ?: return emptyList()
    fun numArr(k: String) = (obj[k] as? JsonArray)?.map { (it as? JsonPrimitive)?.doubleOrNull ?: 0.0 } ?: emptyList()
    val st = numArr("start"); val du = numArr("dur")
    val off = (obj["off"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
    val asEnd = du.indices.count { i -> (du[i] > (st.getOrElse(i) { 0.0 })) } > du.size * 0.5 && du.isNotEmpty()
    return words.mapIndexed { i, w ->
        val s = off + (st.getOrElse(i) { 0.0 }) / 100.0
        val e = off + (if (asEnd) du.getOrElse(i) { 0.0 } else (st.getOrElse(i) { 0.0 }) + du.getOrElse(i) { 0.0 }) / 100.0
        AWord(w, s, e.coerceAtLeast(s + 0.04))
    }
}

/** تجميع الكلمات في جُمَل — عند علامات الترقيم أو سكتة > 0.85ث أو 12 كلمة. */
fun buildPhrases(ws: List<AWord>): List<List<AWord>> {
    val pbrk = Regex("[،؛.!؟:…—]$")
    val ph = mutableListOf<MutableList<AWord>>()
    var cur = mutableListOf<AWord>()
    var prevE = -1.0
    for (w in ws) {
        val gap = if (prevE >= 0) w.s - prevE else 0.0
        if (cur.isNotEmpty() && (gap > 0.85 || cur.size >= 12)) { ph.add(cur); cur = mutableListOf() }
        cur.add(w); prevE = w.e
        if (pbrk.containsMatchIn(w.w) && cur.size >= 3) { ph.add(cur); cur = mutableListOf() }
    }
    if (cur.isNotEmpty()) ph.add(cur)
    return ph
}

/** بحث ثنائي سريع لتحديد الجملة الحالية عند الزمن pos. */
fun findPhraseIndex(phrases: List<List<AWord>>, pos: Double): Int {
    if (phrases.isEmpty()) return 0
    var lo = 0
    var hi = phrases.lastIndex
    var ans = phrases.lastIndex
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (pos <= phrases[mid].last().e + 0.35) {
            ans = mid
            hi = mid - 1
        } else {
            lo = mid + 1
        }
    }
    return ans
}
