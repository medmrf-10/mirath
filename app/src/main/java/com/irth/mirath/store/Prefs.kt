package com.irth.mirath.store

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

object Prefs {
    private const val FILE = "mirath.prefs"
    private var sp: SharedPreferences? = null

    val theme = mutableStateOf("night")
    val favs = mutableStateOf<Set<String>>(emptySet())

    fun init(ctx: Context) {
        if (sp != null) return
        sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        theme.value = sp!!.getString("theme", "night") ?: "night"
        favs.value = sp!!.getStringSet("favs", emptySet()) ?: emptySet()
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
    }

    fun openedCount(): Int = sp?.getStringSet("opened", emptySet())?.size ?: 0
}
