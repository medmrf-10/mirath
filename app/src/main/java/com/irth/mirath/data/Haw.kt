package com.irth.mirath.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** كتالوج مشايخ هوشات — المحتوى الكامل (تفريغ/محاذاة/ملخص/أسئلة) من ريو hawshat عبر raw.githubusercontent. */
object Haw {
    const val BASE = "https://raw.githubusercontent.com/medmrf-10/hawshat/main"

    private var booted = false
    private val loaded = mutableSetOf<String>()
    var sheikhNames: Map<String, String> = emptyMap()

    // slug → نوع الطبقة → (رقم الدرس → اسم الملف) — من فهرس index.json لكل سلسلة
    // (الحشو ليس موحداً: 01.align مقابل 001.sum داخل السلسلة نفسها — الخرائط هي العقد)
    private val files = mutableMapOf<String, MutableMap<String, Map<Int, String>>>()

    private suspend fun get(path: String): String = Api.getAbs("$BASE$path")
    private suspend fun getJson(path: String): JsonElement = Api.json.parseToJsonElement(get(path))

    private fun str(el: JsonElement?, d: String = "") = el?.jsonPrimitive?.content ?: d
    private fun arr(el: JsonElement?): JsonArray = el as? JsonArray ?: JsonArray(emptyList())
    private fun durSec(d: String): Int {
        val p = d.split(":").mapNotNull { it.toIntOrNull() }
        return when (p.size) { 3 -> p[0] * 3600 + p[1] * 60 + p[2]; 2 -> p[0] * 60 + p[1]; else -> p.firstOrNull() ?: 0 }
    }
    private fun num3(n: Int) = "%03d".format(n)

    /** يُستدعى من Repo.boot بعد كتالوج إرث — يسجّل شيخاً كـScience وكل سلسلة كقشرة items فارغة. */
    suspend fun boot() {
        if (booted) return
        booted = true
        runCatching {
            val sh = getJson("/playlists/data/sheikhs.json").jsonObject
            sheikhNames = arr(sh["sheikhs"]).associate {
                str(it.jsonObject["slug"]) to str(it.jsonObject["name"], str(it.jsonObject["slug"]))
            }
        }
        val sci = mutableListOf<Science>(); val ser = mutableListOf<Series>()
        val seen = mutableSetOf<String>()
        arr(getJson("/playlists/series/index.json").jsonObject["series"]).forEach { s ->
            val o = s.jsonObject
            val slug = str(o["slug"]); if (slug.isEmpty()) return@forEach
            val sheikh = str(o["sheikh_slug"])
            if (seen.add(sheikh)) sci += Science(id = "H:$sheikh", name = sheikhNames[sheikh] ?: sheikh)
            ser += Series(id = "H:$slug", title = str(o["title"]), science = "H:$sheikh", kind = "hawshat", icon = "book")
        }
        Repo.sciences = (Repo.sciences ?: emptyList()) + sci
        Repo.series = (Repo.series ?: emptyList()) + ser
    }

    /** تعبئة كل سلاسل هوشات في الخلفية — بحث/عدّادات/اقتراحات تعمل فوراً بلا فتح كل سلسلة. */
    fun prefetchAll(scope: CoroutineScope) {
        val shells = Repo.series?.filter { it.kind == "hawshat" }?.map { it.id } ?: return
        scope.launch(Dispatchers.IO) {
            shells.forEach { runCatching { ensure(it) } }
        }
    }

    /** يُستدعى عند فتح سلسلة هوشات — يحمّل الدروس وطبقاتها ويسجّل الفيديوهات وخرائط أسماء الملفات. */
    suspend fun ensure(seriesId: String) {
        if (!seriesId.startsWith("H:") || seriesId in loaded) return
        loaded += seriesId
        val slug = seriesId.removePrefix("H:")
        val sJson = runCatching { getJson("/playlists/series/$slug.json").jsonObject }.getOrNull() ?: return
        val lIdx = runCatching { getJson("/playlists/lessons/$slug/index.json").jsonObject }.getOrNull()
        fun nums(k: String) = lIdx?.get(k)?.let { el -> (el as? JsonArray)?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() }?.toSet() } ?: emptySet<Int>()
        fun fmap(k: String) = (lIdx?.get(k) as? JsonObject)?.mapNotNull { (kk, v) ->
            kk.toIntOrNull()?.let { it to v.jsonPrimitive.content }
        }?.toMap() ?: emptyMap()
        val fm = files.getOrPut(slug) { mutableMapOf() }
        fm["align"] = fmap("af"); fm["txt"] = fmap("tf"); fm["sum"] = fmap("sumf"); fm["qu"] = fmap("quf")
        val hasAlign = nums("align"); val hasTxt = nums("txt"); val hasSum = nums("sum"); val hasQu = nums("qu")
        val sheikh = sheikhNames[str(sJson["sheikh_slug"])] ?: str(sJson["sheikh"])
        val vids = mutableListOf<Video>(); val items = mutableListOf<String>()
        arr(sJson["lessons"]).forEach { l ->
            val o = l.jsonObject; val n = str(o["n"]).toIntOrNull() ?: return@forEach
            val id = "H:$slug:${num3(n)}"
            val has = buildList {
                if (n in hasAlign) add("align")
                if (n in hasTxt) add("transcript")
                if (n in hasSum) add("summary")
                if (n in hasQu) add("questions")
            }
            items += id
            vids += Video(id = id, title = str(o["title"]).ifEmpty { "الدرس $n" }, yt = str(o["video"]),
                duration = durSec(str(o["dur"])), has = has, speakers = listOf(sheikh))
        }
        Repo.videos = (Repo.videos ?: emptyList()).filter { !it.id.startsWith("$seriesId:") } + vids
        Repo.series = Repo.series?.map { if (it.id == seriesId) it.copy(items = items) else it }
        com.irth.mirath.store.Prefs.srcRev.value++
    }

    /** اسم ملف الطبقة لدرس — من خرائط الفهرس، مع بديل %03d عند غيابها. */
    private fun file(slug: String, n: Int, layer: String, suffix: String): String =
        files[slug]?.get(layer)?.get(n) ?: "${num3(n)}.$suffix"

    private fun pathOf(id: String): Pair<String, Int>? {
        if (!id.startsWith("H:")) return null
        val p = id.removePrefix("H:").split(":")
        return p[0] to (p.getOrNull(1)?.toIntOrNull() ?: return null)
    }

    suspend fun align(id: String): List<AWord> {
        val (slug, n) = pathOf(id) ?: return emptyList()
        val el = runCatching { getJson("/playlists/lessons/$slug/${file(slug, n, "align", "align.json")}") }.getOrNull() ?: return emptyList()
        // أوقات الكلمات في هوشات مطلقة بالثواني (تحوي الإزاحة أصلاً) — لا تُضاف off
        val o = el.jsonObject
        return arr(o["w"]).mapNotNull { w ->
            val a = w as? JsonArray ?: return@mapNotNull null
            val word = a.getOrNull(0)?.jsonPrimitive?.content ?: return@mapNotNull null
            val s = a.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val e = a.getOrNull(2)?.jsonPrimitive?.doubleOrNull ?: s
            AWord(word, s, e)
        }
    }
    suspend fun transcript(id: String): String {
        val (slug, n) = pathOf(id) ?: return ""
        return runCatching { get("/playlists/lessons/$slug/${file(slug, n, "txt", "txt")}") }.getOrDefault("")
    }
    suspend fun summary(id: String): Summary? {
        val (slug, n) = pathOf(id) ?: return null
        return runCatching { Api.decode<Summary>(getJson("/playlists/lessons/$slug/${file(slug, n, "sum", "sum.json")}")) }.getOrNull()
    }
    suspend fun questions(id: String): List<QA> {
        val (slug, n) = pathOf(id) ?: return emptyList()
        return runCatching {
            val o = getJson("/playlists/lessons/$slug/${file(slug, n, "qu", "qu.json")}").jsonObject
            val qaEl = o["qa"] ?: o["questions"] ?: return@runCatching emptyList()
            Api.decode<Questions>(JsonObject(mapOf("questions" to qaEl))).questions
        }.getOrDefault(emptyList())
    }
}
