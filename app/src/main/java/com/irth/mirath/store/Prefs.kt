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
}
