package com.irth.mirath.data

import kotlinx.serialization.json.jsonObject

/** مستودع البيانات بذاكرة مؤقتة — نفس عقد موقع إرث. */
object Repo {
    var sciences: List<Science>? = null
    var series: List<Series>? = null
    var videos: List<Video>? = null
    var persons: List<Person>? = null
    var works: List<Work>? = null

    private val lessonCache = mutableMapOf<String, LessonData>()

    data class LessonData(
        var align: List<AWord>? = null,
        var transcript: String? = null,
        var summary: Summary? = null,
        var questions: List<QA>? = null,
    )

    var localOn = false

    suspend fun boot() {
        if (sciences != null) return
        sciences = Api.decode<Rows<Science>>(Api.getJson("/data/sciences.json")).rows
        series = Api.decode<Rows<Series>>(Api.getJson("/data/tube/series.json")).rows
        videos = Api.decode<Rows<Video>>(Api.getJson("/data/tube/videos.json")).rows
        persons = Api.decode<Rows<Person>>(Api.getJson("/data/persons.json")).rows
        works = Api.decode<Rows<Work>>(Api.getJson("/data/works.json")).rows
        mergeLocal()
    }

    /** دمج المكتبة المحلية (ملفات الهاتف) مع كتالوج إرث — تُستدعى بعد كل استيراد. */
    fun mergeLocal() {
        val ls = LocalLib.localSeries()
        val lv = LocalLib.localVideos()
        localOn = ls.isNotEmpty()
        series = (series ?: emptyList()) + ls
        videos = (videos ?: emptyList()) + lv
        // دمج أنواع الملفات الملحقة محلياً بدروس الكتالوج في أعلام has
        videos = videos?.map { v ->
            if (v.id.startsWith("L:")) v else {
                val extra = LocalLib.shadowKinds(v.id).filter { it !in v.has }
                if (extra.isEmpty()) v else v.copy(has = v.has + extra)
            }
        }
        LocalLib.localScience()?.let { s ->
            if (sciences?.none { it.id == s.id } == true) sciences = sciences!! + s
        }
    }

    fun video(id: String): Video? = videos?.firstOrNull { it.id == id }
    fun seriesOf(id: String): Series? = series?.firstOrNull { it.id == id }
    fun person(id: String): Person? = persons?.firstOrNull { it.id == id }
    fun work(id: String): Work? = works?.firstOrNull { it.id == id }
    fun seriesOfScience(sid: String): List<Series> = series?.filter { it.science == sid } ?: emptyList()
    fun seriesOfVideo(vid: String): Series? = series?.firstOrNull { it.items.contains(vid) }
    fun worksOfScience(sid: String): List<Work> = works?.filter { it.science == sid } ?: emptyList()
    fun personName(id: String): String = person(id)?.short ?: person(id)?.name ?: id

    suspend fun lesson(id: String): LessonData = lessonCache.getOrPut(id) { LessonData() }

    suspend fun align(id: String): List<AWord> {
        val l = lesson(id)
        if (l.align == null) {
            LocalLib.alignEl(id)?.let { runCatching { l.align = parseAlign(it) } }
            if (l.align == null) {
                runCatching { l.align = parseAlign(Api.getJson("/content/tube/$id/align.json")) }
            }
            if (l.align == null) l.align = emptyList()
        }
        return l.align!!
    }

    suspend fun transcript(id: String): String {
        val l = lesson(id)
        if (l.transcript == null) l.transcript = LocalLib.transcript(id)
            ?: runCatching { Api.getText("/content/tube/$id/transcript.txt") }.getOrDefault("")
        return l.transcript!!
    }

    suspend fun summary(id: String): Summary? {
        val l = lesson(id)
        if (l.summary == null) {
            runCatching {
                val o = LocalLib.summaryEl(id)?.jsonObject ?: Api.getJson("/content/tube/$id/summary.json").jsonObject
                l.summary = Api.decode<Summary>(o)
            }
        }
        return l.summary
    }

    suspend fun questions(id: String): List<QA> {
        val l = lesson(id)
        if (l.questions == null) {
            l.questions = runCatching {
                val el = LocalLib.questionsEl(id) ?: Api.getJson("/content/tube/$id/questions.json")
                Api.decode<Questions>(el).questions
            }.getOrDefault(emptyList())
        }
        return l.questions!!
    }

    fun search(q: String): List<Video> {
        val t = q.trim()
        if (t.isEmpty()) return emptyList()
        return videos?.filter { it.title.contains(t) || it.ytTitle.contains(t) } ?: emptyList()
    }
}
