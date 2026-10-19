package com.irth.mirath.data

import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

data class LessonData(
    var align: List<AWord>? = null,
    var transcript: String? = null,
    var summary: Summary? = null,
    var questions: List<QA>? = null,
)

object Repo {
    var sciences: List<Science>? = null
    var series: List<Series>? = null
    var videos: List<Video>? = null
    var persons: List<Person>? = null
    var works: List<Work>? = null

    private var baseSciences: List<Science> = emptyList()
    private var baseSeries: List<Series> = emptyList()
    private var baseVideos: List<Video> = emptyList()

    private val byId = ConcurrentHashMap<String, Video>()
    private val personById = ConcurrentHashMap<String, Person>()
    private val lessonCache = ConcurrentHashMap<String, LessonData>()

    suspend fun boot() = coroutineScope {
        if (sciences != null) return@coroutineScope
        val hb = runCatching { Haw.boot() }.getOrNull()
        baseSciences = hb?.sciences ?: emptyList()
        baseSeries = hb?.series ?: emptyList()
        baseVideos = hb?.videos ?: emptyList()
        persons = hb?.persons ?: emptyList()
        works = emptyList()
        personById.clear()
        persons!!.forEach { personById[it.id] = it }
        mergeLocal()
    }

    /** دمج تحديث catalog.json الخلفي دون مسح تفاصيل السلاسل التي فُتحت بالفعل. */
    fun applyHawRefresh(hb: Haw.Boot) {
        val currentSeriesById = baseSeries.associateBy { it.id }
        val currentVideosById = byId.toMap()
        baseSciences = hb.sciences
        persons = hb.persons
        personById.clear()
        hb.persons.forEach { personById[it.id] = it }

        baseSeries = hb.series.map { freshSer ->
            val existing = currentSeriesById[freshSer.id]
            // إذا كانت السلسلة قد جُلبت تفاصيلها الفعلية (أول درس له معرف يوتيوب غير فارغ)، نحافظ على قائمة دروسها
            val firstVid = existing?.items?.firstOrNull()?.let { currentVideosById[it] }
            if (existing != null && firstVid != null && firstVid.yt.isNotEmpty()) {
                existing.copy(
                    title = freshSer.title,
                    category = freshSer.category,
                    alignedCount = maxOf(existing.alignedCount, freshSer.alignedCount),
                    totalDur = if (existing.totalDur > 0) existing.totalDur else freshSer.totalDur,
                )
            } else freshSer
        }

        val freshVideosMap = hb.videos.associateBy { it.id }.toMutableMap()
        currentVideosById.forEach { (k, v) ->
            if (k.startsWith("H:") && v.yt.isNotEmpty()) {
                freshVideosMap[k] = v
            }
        }
        baseVideos = freshVideosMap.values.toList()
        mergeLocal()
        Haw.rev.value++
    }

    /** استبدال دروس سلسلة من ytb بعد جلب <sheikh>/<s_code>/_index.json الخاص بها. */
    fun replaceHawSeriesLessons(
        seriesId: String,
        realItemIds: List<String>,
        realVideos: List<Video>,
        alignedCnt: Int,
        totalDur: Int,
    ) {
        baseSeries = baseSeries.map { s ->
            if (s.id == seriesId) s.copy(items = realItemIds, alignedCount = alignedCnt, totalDur = totalDur) else s
        }
        val prefix = "$seriesId:"
        val realMap = realVideos.associateBy { it.id }
        val kept = baseVideos.filterNot { it.id.startsWith(prefix) }
        baseVideos = kept + realMap.values
        mergeLocal()
    }

    var localOn = false

    /** إعادة دمج المكتبة المحلية فوق الكتالوج — يُستدعى بعد أي استيراد أو تعديل محلي. */
    fun mergeLocal() {
        val ls = LocalLib.localSeries()
        val lv = LocalLib.localVideos()
        localOn = ls.isNotEmpty()
        sciences = baseSciences + listOfNotNull(LocalLib.localScience())
        series = baseSeries + ls
        val shadowed = baseVideos.map { v ->
            val sk = LocalLib.shadowKinds(v.id)
            if (sk.isEmpty()) v else v.copy(has = (v.has + sk).distinct())
        }
        videos = shadowed + lv
        byId.clear()
        videos!!.forEach { byId[it.id] = it }
    }

    fun invalidateLesson(id: String) { lessonCache.remove(id) }

    fun video(id: String): Video? = byId[id]
    fun person(id: String): Person? = personById[id]
    fun work(id: String): Work? = works?.firstOrNull { it.id == id }
    fun personName(id: String): String = personById[id]?.let { it.short.ifEmpty { it.name } } ?: id
    fun seriesOf(id: String): Series? = series?.firstOrNull { it.id == id }
    fun seriesOfScience(sid: String): List<Series> = series?.filter { it.science == sid } ?: emptyList()
    fun seriesOfVideo(vid: String): Series? = series?.firstOrNull { it.items.contains(vid) }
    fun worksOfScience(sid: String): List<Work> = works?.filter { it.science == sid } ?: emptyList()

    fun search(q: String): List<Video> {
        val fq = TextIndex.fold(TextIndex.clean(q))
        if (fq.isEmpty()) return emptyList()
        return videos?.filter {
            TextIndex.fold(TextIndex.clean(it.title)).contains(fq) ||
                (it.ytTitle.isNotEmpty() && TextIndex.fold(TextIndex.clean(it.ytTitle)).contains(fq))
        } ?: emptyList()
    }

    suspend fun lesson(id: String): LessonData = lessonCache.getOrPut(id) { LessonData() }

    /** جلب المحاذاة بلا تخزين في الذاكرة — للفهرسة الجماعية أو العرض. */
    suspend fun loadAlign(id: String, cache: Boolean = true): List<AWord> {
        LocalLib.alignEl(id)?.let { el ->
            runCatching { parseAlign(el) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        if (id.startsWith("H:")) {
            return runCatching { Haw.align(id, cache) }.getOrDefault(emptyList())
        }
        return emptyList()
    }

    suspend fun loadTranscript(id: String, cache: Boolean = true): String =
        LocalLib.transcript(id)
            ?: if (id.startsWith("H:")) runCatching { Haw.transcript(id, cache) }.getOrDefault("") else ""

    suspend fun align(id: String): List<AWord> {
        val l = lesson(id)
        if (l.align == null) {
            l.align = loadAlign(id)
            if (l.align!!.isNotEmpty()) TextIndex.putAlign(id, l.align!!)
        }
        return l.align!!
    }

    suspend fun transcript(id: String): String {
        val l = lesson(id)
        if (l.transcript == null) {
            l.transcript = loadTranscript(id)
            if (l.transcript!!.isNotBlank()) TextIndex.putText(id, l.transcript!!, video(id)?.duration ?: 0)
        }
        return l.transcript!!
    }

    suspend fun summary(id: String): Summary? {
        val l = lesson(id)
        if (l.summary == null) {
            LocalLib.summaryEl(id)?.let { runCatching { l.summary = Api.decode(it) } }
            if (l.summary == null && id.startsWith("H:")) {
                runCatching { l.summary = Haw.summary(id) }
            }
        }
        return l.summary
    }

    suspend fun questions(id: String): List<QA> {
        val l = lesson(id)
        if (l.questions == null) {
            LocalLib.questionsEl(id)?.let { el ->
                runCatching { l.questions = Api.decode<Questions>(el).questions }
                    .onFailure { runCatching { l.questions = Api.decode<List<QA>>(el) } }
            }
            if (l.questions == null && id.startsWith("H:")) {
                runCatching { l.questions = Haw.questions(id) }
            }
            if (l.questions == null) l.questions = emptyList()
        }
        return l.questions!!
    }
}
