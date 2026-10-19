package com.irth.mirath.data

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * محول المستودع الموحد ytb (medmrf-10/ytb):
 * - يقرأ catalog.json (25 شيخاً، 386 سلسلة، 6,589 درساً مفرغاً، و1,180+ درساً محاذى كلمة بكلمة).
 * - معرف الشيخ: "H:<sheikh_slug>"
 * - معرف السلسلة: "H:<sheikh_slug>/<s_code>"
 * - معرف الدرس: "H:<sheikh_slug>/<s_code>:<n>"
 * - عند فتح سلسلة يُجلب <sheikh_slug>/<s_code>/_index.json مرة واحدة لتعبئة العناوين والمدد والمحاذاة.
 */
object Haw {
    const val BASE = "https://raw.githubusercontent.com/medmrf-10/ytb/main"
    private val ensured = ConcurrentHashMap.newKeySet<String>()
    private val lock = Mutex()
    // lid ("H:said-bouti/s001:1") -> مسار الملف الأساسي بدون امتداد ("said-bouti/s001/001_MgujOjDesPA")
    private val stems = ConcurrentHashMap<String, String>()
    private val photos = ConcurrentHashMap<String, String>()

    /** صورة الشيخ بمعرفه «H:<slug>» إن وُجدت. */
    fun photoOf(id: String): String? = photos[id]

    /** نبض إعادة التركيب عند اكتمال تحميل تفاصيل سلسلة أو تنزيلها. */
    val rev = mutableStateOf(0)

    /** نطاق خلفي حي للتنزيل والفهرسة والتحديث لا يُلغى بمغادرة الشاشة. */
    val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** حالة تنزيل سلسلة أو شيخ: seriesId -> (المكتمل، الإجمالي). */
    val downloading = mutableStateOf<Map<String, Pair<Int, Int>>>(emptyMap())

    private suspend fun get(path: String, cache: Boolean = true, forceRefresh: Boolean = false): String =
        Api.getAbs("$BASE$path", cache, forceRefresh)

    private suspend fun getJson(path: String, cache: Boolean = true, forceRefresh: Boolean = false): JsonElement =
        Api.json.parseToJsonElement(get(path, cache, forceRefresh))

    private fun str(el: JsonElement?, d: String = ""): String =
        (el as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull ?: d

    private fun int(el: JsonElement?, d: Int = 0): Int =
        (el as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull ?: d

    private fun dbl(el: JsonElement?, d: Double = 0.0): Double =
        (el as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull ?: d

    private fun bool(el: JsonElement?, d: Boolean = false): Boolean =
        (el as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull ?: d

    private fun arr(el: JsonElement?): JsonArray = el as? JsonArray ?: JsonArray(emptyList())

    data class Boot(
        val sciences: List<Science>,
        val series: List<Series>,
        val videos: List<Video>,
        val persons: List<Person>,
    )

    suspend fun boot(): Boot = coroutineScope {
        val catEl = getJson("/catalog.json", cache = true)
        val parsed = parseCatalog(catEl)
        // تحديث صامت في الخلفية لـ catalog.json لضمان ظهور المحاذايات الجديدة دائماً
        bg.launch {
            runCatching { refreshCatalog() }
        }
        parsed
    }

    /** تحديث مباشر للفهرس الحي catalog.json من مستودع ytb. */
    suspend fun refreshCatalog(): Boolean = runCatching {
        val freshEl = getJson("/catalog.json", cache = true, forceRefresh = true)
        val fresh = parseCatalog(freshEl)
        Repo.applyHawRefresh(fresh)
        com.irth.mirath.store.Prefs.srcRev.value++
        true
    }.getOrDefault(false)

    /** جلب مسبق خفيف لفهارس السلاسل التي تحتوي على محاذاة أو تقدم سابق. */
    fun prefetchAll(scope: CoroutineScope = bg) {
        scope.launch(Dispatchers.IO) {
            val sers = Repo.series ?: return@launch
            val sem = Semaphore(4)
            sers.filter { it.id.startsWith("H:") && it.alignedCount > 0 }.take(24).map { s ->
                async {
                    sem.withPermit {
                        runCatching { ensure(s.id) }
                    }
                }
            }.awaitAll()
            com.irth.mirath.store.Prefs.srcRev.value++
        }
    }

    private suspend fun parseCatalog(catEl: JsonElement): Boot = coroutineScope {
        val shArr = arr(catEl.jsonObject["sheikhs"])
        val sciences = mutableListOf<Science>()
        val persons = mutableListOf<Person>()
        val seriesOut = mutableListOf<Series>()
        val videosOut = mutableListOf<Video>()

        for (sEl in shArr) {
            val o = sEl.jsonObject
            val slug = str(o["slug"])
            if (slug.isEmpty() || slug == "unattributed") continue
            val name = str(o["name_display"]).ifEmpty { str(o["name"], slug) }
            val photoRel = str(o["photo"])
            val photoUrl = if (photoRel.isNotEmpty()) "$BASE/${photoRel.trimStart('/')}" else ""
            if (photoUrl.isNotEmpty()) photos["H:$slug"] = photoUrl

            val sCount = int(o["series"])
            val lCount = int(o["lessons"])
            val aCount = int(o["aligned"])

            sciences += Science(
                id = "H:$slug",
                name = name,
                seriesCount = sCount,
                lessonsCount = lCount,
                alignedCount = aCount,
                photo = photoUrl,
            )
            persons += Person(id = "H:$slug", name = name, short = name, photo = photoUrl)

            var sList = arr(o["series_list"])
            if (sList.isEmpty()) {
                sList = runCatching { arr(getJson("/$slug/_index.json").jsonObject["series"]) }.getOrDefault(JsonArray(emptyList()))
            }

            for (serEl in sList) {
                val so = serEl.jsonObject
                val sk = str(so["key"])
                if (sk.isEmpty()) continue
                val sTitle = str(so["title"], sk)
                val cat = str(so["category"])
                val count = int(so["transcribed"]).coerceAtLeast(int(so["declared_total"]))
                if (count <= 0) continue
                val aligned = int(so["aligned"])
                val totalDur = int(so["dur"])
                val avgDur = if (totalDur > 0 && count > 0) (totalDur / count) else 1800

                val seriesSlug = "$slug/$sk"
                val sid = "H:$seriesSlug"
                val itemIds = (1..count).map { n -> "H:$seriesSlug:$n" }
                val hasFlags = buildList {
                    add("transcript")
                    if (aligned > 0) add("align")
                }

                seriesOut += Series(
                    id = sid,
                    title = sTitle,
                    science = "H:$slug",
                    kind = cat,
                    items = itemIds,
                    category = cat,
                    alignedCount = aligned,
                    totalDur = totalDur,
                )
                for (n in 1..count) {
                    videosOut += Video(
                        id = "H:$seriesSlug:$n",
                        title = "الدرس $n — $sTitle",
                        speakers = listOf("H:$slug"),
                        duration = avgDur,
                        has = hasFlags,
                    )
                }
            }
        }
        Boot(sciences, seriesOut, videosOut, persons)
    }

    /** يجلب <sheikh>/<s_code>/_index.json لسلسلة مرة واحدة ويملأ عناوين الدروس ومعرفات يوتيوب والمدد والمحاذاة. */
    suspend fun ensure(seriesId: String, forceRefresh: Boolean = false) {
        if (!seriesId.startsWith("H:")) return
        val seriesSlug = seriesId.removePrefix("H:")
        if (!seriesSlug.contains("/")) return
        if (!forceRefresh && ensured.contains(seriesSlug)) return

        val idxEl = runCatching {
            getJson("/$seriesSlug/_index.json", cache = true, forceRefresh = forceRefresh)
        }.getOrNull() ?: return

        val idxObj = idxEl.jsonObject
        val sheikhSlug = str(idxObj["sheikh"]).ifEmpty { seriesSlug.substringBefore("/") }
        val sTitle = str(idxObj["title"], seriesSlug)
        val lessonsArr = arr(idxObj["lessons"])
        if (lessonsArr.isEmpty()) return

        lock.withLock {
            if (!forceRefresh && ensured.contains(seriesSlug)) return
            val realItemIds = mutableListOf<String>()
            val realVideos = mutableListOf<Video>()
            var alignedCnt = 0
            var totalDur = 0

            lessonsArr.forEachIndexed { idx, lEl ->
                val lo = lEl.jsonObject
                val n = int(lo["n"], idx + 1)
                val vid = str(lo["vid"])
                val file = str(lo["file"])
                val stem = str(lo["stem"]).ifEmpty {
                    if (file.endsWith(".txt")) file.removeSuffix(".txt") else "%03d_%s".format(n, vid)
                }
                val title = str(lo["title"]).ifEmpty { "الدرس $n — $sTitle" }
                val dur = int(lo["dur"], 1800)
                val aligned = bool(lo["aligned"])
                val alignCov = dbl(lo["align_coverage"])

                if (aligned) alignedCnt++
                totalDur += dur

                val lid = "H:$seriesSlug:$n"
                stems[lid] = "$seriesSlug/$stem"
                realItemIds += lid

                val has = buildList {
                    add("transcript")
                    if (aligned) add("align")
                    addAll(LocalLib.shadowKinds(lid))
                }.distinct()

                realVideos += Video(
                    id = lid,
                    title = title,
                    yt = vid,
                    speakers = listOf("H:$sheikhSlug"),
                    duration = dur,
                    has = has,
                    alignCov = alignCov,
                )
            }

            Repo.replaceHawSeriesLessons(seriesId, realItemIds, realVideos, alignedCnt, totalDur)
            ensured += seriesSlug
            rev.value++
        }

        // إن لم يكن استدعاء تحديث مباشر، نتحقق في الخلفية من أحدث نسخة من _index.json
        if (!forceRefresh) {
            bg.launch {
                runCatching { ensure(seriesId, forceRefresh = true) }
            }
        }
    }

    private suspend fun stemPathOf(id: String): String? {
        if (!id.startsWith("H:")) return null
        stems[id]?.let { return it }
        val body = id.removePrefix("H:")
        val seriesSlug = body.substringBeforeLast(":", "")
        if (seriesSlug.isEmpty() || !seriesSlug.contains("/")) return null
        ensure("H:$seriesSlug")
        return stems[id]
    }

    suspend fun align(id: String, cache: Boolean = true): List<AWord> {
        val stemPath = stemPathOf(id) ?: return emptyList()
        val el = runCatching { getJson("/$stemPath.align.json", cache) }.getOrNull() ?: return emptyList()
        return runCatching { parseAlign(el) }.getOrDefault(emptyList())
    }

    suspend fun transcript(id: String, cache: Boolean = true): String {
        val stemPath = stemPathOf(id) ?: return ""
        return runCatching { get("/$stemPath.txt", cache) }.getOrDefault("")
    }

    suspend fun summary(id: String): Summary? = null
    suspend fun questions(id: String): List<QA> = emptyList()

    /** هل كل دروس السلسلة محفوظة في الكاش المحلي (تعمل بلا إنترنت)؟ */
    fun seriesCached(seriesId: String): Boolean {
        if (!seriesId.startsWith("H:")) return false
        val seriesSlug = seriesId.removePrefix("H:")
        if (!ensured.contains(seriesSlug)) return false
        val s = Repo.seriesOf(seriesId) ?: return false
        return s.items.isNotEmpty() && s.items.all { lid ->
            val sp = stems[lid] ?: return@all false
            Api.cachedFile("$BASE/$sp.txt") != null
        }
    }

    /** تنزيل نصوص ومحاذاة السلسلة كاملة للكاش المحلي (تستمر في الخلفية). */
    fun startDownloadSeries(seriesId: String) {
        if (!seriesId.startsWith("H:") || downloading.value.containsKey(seriesId)) return
        bg.launch { downloadSeriesSuspend(seriesId) }
    }

    private suspend fun downloadSeriesSuspend(seriesId: String) = coroutineScope {
        if (!seriesId.startsWith("H:") || downloading.value.containsKey(seriesId)) return@coroutineScope
        try {
            ensure(seriesId)
            val s = Repo.seriesOf(seriesId) ?: return@coroutineScope
            val total = s.items.size
            if (total == 0) return@coroutineScope
            downloading.value = downloading.value + (seriesId to (0 to total))
            val sem = Semaphore(6)
            var done = 0
            s.items.map { lid ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        val v = Repo.video(lid)
                        if (v?.has?.contains("transcript") == true) runCatching { Repo.transcript(lid) }
                        if (v?.has?.contains("align") == true) runCatching { Repo.align(lid) }
                        lock.withLock {
                            done++
                            downloading.value = downloading.value + (seriesId to (done to total))
                        }
                    }
                }
            }.awaitAll()
        } finally {
            downloading.value = downloading.value - seriesId
            rev.value++
        }
    }

    /** تنزيل كل سلاسل الشيخ تتابعياً في الخلفية. */
    fun startDownloadSheikh(scienceId: String) {
        if (!scienceId.startsWith("H:") || downloading.value.containsKey(scienceId)) return
        bg.launch {
            val all = Repo.series?.filter { it.science == scienceId } ?: return@launch
            if (all.isEmpty()) return@launch
            downloading.value = downloading.value + (scienceId to (0 to all.size))
            try {
                all.forEachIndexed { idx, s ->
                    downloadSeriesSuspend(s.id)
                    downloading.value = downloading.value + (scienceId to ((idx + 1) to all.size))
                }
            } finally {
                downloading.value = downloading.value - scienceId
                rev.value++
            }
        }
    }
}
