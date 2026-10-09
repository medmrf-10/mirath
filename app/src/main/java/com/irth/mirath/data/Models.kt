package com.irth.mirath.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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

/** كلمة محاذاة: w[i] = [الكلمة, البداية, النهاية, رقم السطر] */
data class AWord(val w: String, val s: Double, val e: Double, val line: Int)

fun parseAlign(el: JsonElement): List<AWord> {
    val obj = el as? kotlinx.serialization.json.JsonObject ?: return emptyList()
    val w = obj["w"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
    return w.mapNotNull { it ->
        val a = it as? kotlinx.serialization.json.JsonArray ?: return@mapNotNull null
        if (a.size < 4) return@mapNotNull null
        AWord(
            a[0].toString().trim('"'),
            a[1].toString().toDoubleOrNull() ?: 0.0,
            a[2].toString().toDoubleOrNull() ?: 0.0,
            a[3].toString().toIntOrNull() ?: 0,
        )
    }
}
