package com.irth.mirath.store

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

object Prefs {
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

    /** مصدر تشغيل السلسلة: "yt" يوتيوب (افتراضي) أو "local" وسائط منزّلة. */
    fun src(seriesId: String): String = sp?.getString("src.$seriesId", "yt") ?: "yt"
    fun setSrc(seriesId: String, v: String) {
        sp?.edit()?.putString("src.$seriesId", v)?.apply()
        srcRev.value++
    }
    val srcRev = mutableStateOf(0)
}
