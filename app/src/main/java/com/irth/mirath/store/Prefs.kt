package com.irth.mirath.store

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Serializable
data class Bookmark(val s: Int, val n: String = "", val t: Long = 0)

object Prefs {
    private val json = Json { ignoreUnknownKeys = true }
    private const val FILE = "mirath.prefs"
    private var sp: SharedPreferences? = null

    val theme = mutableStateOf("night")
    val favs = mutableStateOf<Set<String>>(emptySet())
    val recent = mutableStateOf<List<String>>(emptyList())
    val qrate = mutableStateOf<Map<String, Int>>(emptyMap())

    fun init(ctx: Context) {
        if (sp != null) return
        sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        theme.value = sp!!.getString("theme", "night") ?: "night"
        favs.value = sp!!.getStringSet("favs", emptySet()) ?: emptySet()
        recent.value = (sp!!.getString("recent", "") ?: "").split(",").filter { it.isNotBlank() }
        qrate.value = (sp!!.getString("qrate", "") ?: "")
            .split(",").filter { it.contains(":") }
            .associate { it.substringBefore(":") to (it.substringAfter(":").toIntOrNull() ?: 0) }
        streak.value = sp!!.getInt("streak", 0)
    }

    fun setTheme(t: String) {
        theme.value = t
        sp?.edit()?.putString("theme", t)?.apply()
    }

    fun isFav(id: String) = favs.value.contains(id)

    fun toggleFav(id: String) {
        val s = favs.value.toMutableSet()
        if (!s.add(id)) s.remove(id)
        favs.value = s
        sp?.edit()?.putStringSet("favs", s)?.apply()
    }

    fun progress(id: String): Int = sp?.getInt("prg.$id", 0) ?: 0

    fun setProgress(id: String, sec: Int) {
        sp?.edit()?.putInt("prg.$id", sec)?.apply()
    }

    fun markOpened(id: String) {
        val s = (sp?.getStringSet("opened", emptySet()) ?: emptySet()).toMutableSet()
        if (s.add(id)) sp?.edit()?.putStringSet("opened", s)?.apply()
        recent.value = (listOf(id) + recent.value.filter { it != id }).take(50)
        sp?.edit()?.putString("recent", recent.value.joinToString(","))?.apply()
    }

    fun last(): String? = recent.value.firstOrNull()

    fun openedCount(): Int = sp?.getStringSet("opened", emptySet())?.size ?: 0

    fun rate(id: String, i: Int): Int = qrate.value["$id#$i"] ?: 0

    fun setRate(id: String, i: Int, n: Int) {
        qrate.value = qrate.value + ("$id#$i" to n)
        sp?.edit()?.putString("qrate", qrate.value.entries.joinToString(",") { "${it.key}:${it.value}" })?.apply()
    }

    /** تقدّم «قيد المشاهدة» بقاعدة إرث: أكثر من 10 ثوانٍ ولم يبلغ 95%. */
    fun ongoing(id: String, duration: Int): Boolean {
        val pv = progress(id)
        return pv > 10 && (duration <= 0 || pv < duration * 0.95)
    }

    fun finished(id: String, duration: Int): Boolean =
        duration > 0 && progress(id) >= duration * 0.95

    val streak = mutableStateOf(0)

    private fun dayOf(ms: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(ms))

    /** يُستدعى عند بدء تشغيل فعلي — يحسب أيام المواظبة المتتالية. */
    fun touchActivity() {
        val today = dayOf(System.currentTimeMillis())
        val last = sp?.getString("lastActive", "") ?: ""
        if (last == today) return
        val yesterday = dayOf(System.currentTimeMillis() - 86400000)
        streak.value = if (last == yesterday) (sp?.getInt("streak", 0) ?: 0) + 1 else 1
        sp?.edit()?.putInt("streak", streak.value)?.putString("lastActive", today)?.apply()
    }

    fun streakDays(): Int {
        val last = sp?.getString("lastActive", "") ?: return 0
        val t = runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(last)?.time ?: return 0
        }.getOrDefault(0L)
        if (t == 0L) return 0
        val days = (System.currentTimeMillis() - t) / 86400000
        return if (days <= 1) sp?.getInt("streak", 0) ?: 0 else 0
    }

    /** كل مواضع التقدّم المحفوظة: videoId → ثوانٍ. */
    fun progressIndex(): Map<String, Int> =
        sp?.all?.filterKeys { it.startsWith("prg.") }
            ?.mapKeys { it.key.removePrefix("prg.") }
            ?.mapValues { (it.value as? Int) ?: 0 } ?: emptyMap()

    val bmRev = mutableStateOf(0)

    fun bookmarks(id: String): List<Bookmark> =
        runCatching { json.decodeFromString<List<Bookmark>>(sp?.getString("bm.$id", "[]") ?: "[]") }
            .getOrDefault(emptyList()).sortedBy { it.s }

    private fun saveBms(id: String, l: List<Bookmark>) {
        if (l.isEmpty()) sp?.edit()?.remove("bm.$id")?.apply()
        else sp?.edit()?.putString("bm.$id", json.encodeToString(l.sortedBy { it.s }))?.apply()
        bmRev.value++
    }

    /** علامة عند الموضع الحالي؛ true إن أُضيفت، false إن أُزيلت (موجودة ±3 ثوانٍ). */
    fun toggleBookmark(id: String, sec: Int): Boolean {
        val l = bookmarks(id)
        val hit = l.firstOrNull { kotlin.math.abs(it.s - sec) <= 3 }
        if (hit != null) { saveBms(id, l - hit); return false }
        saveBms(id, l + Bookmark(sec, t = System.currentTimeMillis()))
        return true
    }

    fun removeBookmark(id: String, sec: Int) =
        saveBms(id, bookmarks(id).filter { it.s != sec })

    fun setBookmarkNote(id: String, sec: Int, note: String) =
        saveBms(id, bookmarks(id).map { if (it.s == sec) it.copy(n = note.trim()) else it })

    fun hasBookmark(id: String): Boolean = bookmarks(id).isNotEmpty()

    /** كل العلامات عبر الدروس: videoId → قائمتها. */
    fun allBookmarks(): Map<String, List<Bookmark>> =
        sp?.all?.filterKeys { it.startsWith("bm.") }
            ?.mapKeys { it.key.removePrefix("bm.") }
            ?.mapValues { runCatching { json.decodeFromString<List<Bookmark>>(it.value as String) }.getOrDefault(emptyList()) }
            ?.filterValues { it.isNotEmpty() } ?: emptyMap()

    /** مصدر تشغيل السلسلة: "yt" يوتيوب (افتراضي) أو "local" وسائط منزّلة. */
    /** مفاتيح نصية عامة (توكن مزامنة، هوية المستخدم). */
    fun str(k: String): String = sp?.getString("k.$k", "") ?: ""
    fun putStr(k: String, v: String) { sp?.edit()?.putString("k.$k", v)?.apply() }

    fun src(seriesId: String): String = sp?.getString("src.$seriesId", "yt") ?: "yt"
    fun setSrc(seriesId: String, v: String) {
        sp?.edit()?.putString("src.$seriesId", v)?.apply()
        srcRev.value++
    }
    val srcRev = mutableStateOf(0)

    /** كل التفضيلات كـJSON (تقدم/علامات/مفضلة/مواظبة/مصدر السلاسل/ثيم) — للنسخ الاحتياطي. */
    fun exportAll(): String {
        val o = kotlinx.serialization.json.JsonObject(
            (sp?.all ?: emptyMap()).mapValues { (_, v) ->
                when (v) {
                    is Set<*> -> kotlinx.serialization.json.JsonArray(v.filterIsInstance<String>().map { kotlinx.serialization.json.JsonPrimitive(it) })
                    is Int, is Long, is Float, is Double, is Boolean -> kotlinx.serialization.json.JsonPrimitive(v as Number)
                    else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
                }
            }
        )
        return o.toString()
    }

    /** استعادة نسخة احتياطية — يكتب المفاتيح مباشرة ثم ينعش الحالات المتذكرة. */
    fun importAll(jsonStr: String) {
        val o = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(jsonStr).jsonObject
        }.getOrNull() ?: return
        val ed = sp?.edit() ?: return
        o.forEach { (k, el) ->
            val p = el as? kotlinx.serialization.json.JsonPrimitive ?: return@forEach
            when {
                el is kotlinx.serialization.json.JsonArray ->
                    ed.putStringSet(k, el.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.toSet())
                p.isString -> ed.putString(k, p.content)
                p.content == "true" || p.content == "false" -> ed.putBoolean(k, p.content == "true")
                p.content.contains('.') -> ed.putFloat(k, p.content.toFloatOrNull() ?: 0f)
                else -> runCatching { ed.putInt(k, p.content.toInt()) }.onFailure { ed.putLong(k, p.content.toLongOrNull() ?: 0L) }
            }
        }
        ed.apply()
        // انعاش الحالات
        theme.value = sp?.getString("theme", theme.value) ?: theme.value
        favs.value = sp?.getStringSet("favs", favs.value) ?: favs.value
        recent.value = (sp?.getString("recent", "") ?: "").split(",").filter { it.isNotBlank() }
        streak.value = sp?.getInt("streak", 0) ?: 0
        bmRev.value++; srcRev.value++
    }
}
