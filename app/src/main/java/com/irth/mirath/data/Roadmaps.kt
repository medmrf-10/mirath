package com.irth.mirath.data

import android.content.Context
import com.irth.mirath.store.Prefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RStep(val series: String, val lesson: String = "")

@Serializable
data class Roadmap(
    val id: String,
    val title: String,
    val icon: String = "map",
    val desc: String = "",
    val steps: List<RStep> = emptyList(),
)

object Roadmaps {
    private val json = Json { ignoreUnknownKeys = true }
    private fun file(ctx: Context) = java.io.File(ctx.filesDir, "roadmaps.json")

    fun all(ctx: Context): List<Roadmap> = runCatching {
        json.decodeFromString<List<Roadmap>>(file(ctx).readText())
    }.getOrDefault(emptyList())

    fun saveAll(ctx: Context, list: List<Roadmap>) {
        runCatching { file(ctx).writeText(json.encodeToString(list)) }
    }

    fun add(ctx: Context, rm: Roadmap) = saveAll(ctx, all(ctx).filter { it.id != rm.id } + rm)

    fun del(ctx: Context, id: String) = saveAll(ctx, all(ctx).filter { it.id != id })

    fun get(ctx: Context, id: String): Roadmap? = all(ctx).firstOrNull { it.id == id }

    private fun stepLessons(st: RStep): List<String> {
        if (st.lesson.isNotEmpty()) return listOf(st.lesson)
        return Repo.seriesOf(st.series)?.items?.map { it } ?: emptyList()
    }

    fun totals(rm: Roadmap): Pair<Int, Int> {
        var done = 0; var all = 0
        rm.steps.forEach { st ->
            stepLessons(st).forEach { vid ->
                all++
                if (Repo.video(vid)?.let { Prefs.finished(vid, it.duration) } == true) done++
            }
        }
        return done to all
    }

    fun nextLesson(rm: Roadmap): String? {
        rm.steps.forEach { st ->
            stepLessons(st).forEach { vid ->
                if (Repo.video(vid)?.let { Prefs.finished(vid, it.duration) } != true) return vid
            }
        }
        return null
    }
}
