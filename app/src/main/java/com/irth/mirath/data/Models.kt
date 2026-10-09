package com.irth.mirath.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class Rows<T>(val v: Int = 0, val rows: List<T> = emptyList())

@Serializable
data class Science(val id: String = "", val name: String = "")

@Serializable
data class Series(
    val id: String = "",
    val title: String = "",
    val science: String = "",
    val kind: String = "",
    val items: List<String> = emptyList(),
)

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
)

@Serializable
data class Summary(
    val summary: String = "",
    val points: List<String> = emptyList(),
)

@Serializable
data class QA(val q: String = "", val a: String = "")

@Serializable
data class Questions(val questions: List<QA> = emptyList())

/** كلمة محاذاة: الكلمة وبدايتها ونهايتها بالثواني — بنفس normAlign في إرث. */
data class AWord(val w: String, val s: Double, val e: Double)

fun parseAlign(el: JsonElement): List<AWord> {
    val obj = el as? kotlinx.serialization.json.JsonObject ?: return emptyList()
    val words = (obj["words"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.split(' ')?.filter { it.isNotBlank() } ?: return emptyList()
    fun numArr(k: String) = (obj[k] as? kotlinx.serialization.json.JsonArray)?.map { (it as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull ?: 0.0 } ?: emptyList()
    val st = numArr("start"); val du = numArr("dur")
    val off = (obj["off"] as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull ?: 0.0
    val asEnd = du.indices.count { i -> (du[i] > (st.getOrElse(i) { 0.0 })) } > du.size * 0.5 && du.isNotEmpty()
    return words.mapIndexed { i, w ->
        val s = off + (st.getOrElse(i) { 0.0 }) / 100.0
        val e = off + (if (asEnd) du.getOrElse(i) { 0.0 } else (st.getOrElse(i) { 0.0 }) + du.getOrElse(i) { 0.0 }) / 100.0
        AWord(w, s, e)
    }
}

/** تجميع الكلمات في جُمَل — نسخة حرفية من buildPhrases في إرث. */
fun buildPhrases(ws: List<AWord>): List<List<AWord>> {
    val pbrk = Regex("[،؛.!؟:…—]$")
    val ph = mutableListOf<MutableList<AWord>>()
    var cur = mutableListOf<AWord>()
    var prevE = -1.0
    for (w in ws) {
        val gap = w.s - prevE
        if (cur.isNotEmpty() && (gap > 1.2 || cur.size >= 12)) { ph.add(cur); cur = mutableListOf() }
        cur.add(w); prevE = w.e
        if (pbrk.containsMatchIn(w.w) && cur.size >= 3) { ph.add(cur); cur = mutableListOf() }
    }
    if (cur.isNotEmpty()) ph.add(cur)
    return ph
}
