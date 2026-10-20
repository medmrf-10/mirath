package com.irth.mirath.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * خطب مقسّمة إلى كتل فكرية (سلسلتا الحبيب عمر بن حفيظ ومولود السريري).
 * المصدر الحي: ytb/main/khutab/index.json ثم khutab/<file>؛ والنسخة المضمّنة في assets/khutab تعمل بلا اتصال.
 * صيغة الملفات موثّقة في workspace/khutab/SPEC.md (v2؛ ويقرأ v1 أيضاً).
 */
@Serializable
data class KAuthor(val id: String = "", val name: String = "", val count: Int = 0)

@Serializable
data class KItem(
    val id: String = "",
    val author: String = "",
    val n: Int = 0,
    val title: String = "",
    val topic: String = "",
    val yt: String = "",
    val src: String = "",
    val dur: Int = 0,
    val blocks: Int = 0,
    val chars: Int = 0,
    val has: List<String> = emptyList(),
    val file: String = "",
)

@Serializable
data class KIndex(val v: Int = 0, val authors: List<KAuthor> = emptyList(), val items: List<KItem> = emptyList())

/** كتلة = فكرة واحدة؛ part = 1 للخطبة الأولى و2 للثانية. */
@Serializable
data class KBlock(val id: String = "", val h: String = "", val t: String = "", val part: Int = 1)

/** الأقسام الواقعة خارج جسم الخطبة. */
@Serializable
data class KSections(
    // v2: لكل خطبة مقدمة ودعاء وخاتمة مستقلة
    val intro1: String? = null,
    val dua1: String? = null,
    val close1: String? = null,
    val intro2: String? = null,
    val dua2: String? = null,
    val close2: String? = null,
    // v1 (ملفات قديمة): intro = intro1، dua = دعاء الخطبة الثانية
    val intro: String? = null,
    val dua: String? = null,
) {
    /** (مفتاح، عنوان عربي، النص) بترتيب الخطبة: مقدمة ← دعاء ← خاتمة لكل خطبة، للموجود فقط. */
    fun present(): List<Triple<String, String, String>> = listOfNotNull(
        (intro1 ?: intro)?.let { Triple("intro1", "مقدمة الخطبة الأولى", it) },
        dua1?.let { Triple("dua1", "دعاء الخطبة الأولى", it) },
        close1?.let { Triple("close1", "خاتمة الخطبة الأولى", it) },
        intro2?.let { Triple("intro2", "مقدمة الخطبة الثانية", it) },
        (dua2 ?: dua)?.let { Triple("dua2", "دعاء الخطبة الثانية", it) },
        close2?.let { Triple("close2", "خاتمة الخطبة الثانية", it) },
    ).filter { it.third.isNotBlank() }
}

@Serializable
data class Khutba(
    val id: String = "",
    val author: String = "",
    val authorName: String = "",
    val n: Int = 0,
    val title: String = "",
    val topic: String = "",
    val yt: String = "",
    val src: String = "",
    val dur: Int = 0,
    val sections: KSections = KSections(),
    val blocks: List<KBlock> = emptyList(),
)

object Khutab {
    private const val REMOTE = "${Api.BASE}/khutab"
    private val defaultAuthors = listOf(
        KAuthor("omar", "الحبيب عمر بن حفيظ"),
        KAuthor("sariri", "مولود السريري"),
    )

    private var app: Context? = null
    private val cache = ConcurrentHashMap<String, Khutba>()
    private val remoteIds = ConcurrentHashMap.newKeySet<String>()

    val authors = mutableStateOf(defaultAuthors)
    val items = mutableStateOf<List<KItem>>(emptyList())

    /** يقرأ الفهرس المضمّن فوراً (بلا شبكة)؛ التحديث الحي في [refresh]. */
    fun init(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        assetText("index.json")?.let { txt ->
            runCatching { Api.json.decodeFromString<KIndex>(txt) }.getOrNull()?.let { merge(it, remote = false) }
        }
    }

    private fun assetText(path: String): String? = runCatching {
        app!!.assets.open("khutab/$path").bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun merge(idx: KIndex, remote: Boolean) {
        val byId = LinkedHashMap<String, KItem>()
        items.value.forEach { byId[it.id] = it }
        idx.items.forEach { byId[it.id] = it; if (remote) remoteIds.add(it.id) }
        val all = byId.values.sortedWith(compareBy({ it.author }, { it.n }))
        items.value = all
        val names = (idx.authors + authors.value).filter { it.id.isNotEmpty() }.associateBy { it.id }
        authors.value = defaultAuthors.map { d -> d.copy(name = names[d.id]?.name?.ifEmpty { d.name } ?: d.name, count = all.count { it.author == d.id }) } +
            all.map { it.author }.distinct().filter { a -> defaultAuthors.none { it.id == a } }
                .map { a -> KAuthor(a, names[a]?.name ?: a, all.count { it.author == a }) }
    }

    /** يجلب الفهرس الحي من مستودع ytb ويدمجه فوق المضمّن؛ يتجاهل الفشل بصمت. */
    suspend fun refresh() {
        val txt = runCatching { Api.getAbs("$REMOTE/index.json", cache = true, forceRefresh = true) }.getOrNull() ?: return
        val idx = runCatching { Api.json.decodeFromString<KIndex>(txt) }.getOrNull() ?: return
        merge(idx, remote = true)
    }

    fun item(id: String): KItem? = items.value.firstOrNull { it.id == id }
    fun itemsOf(author: String): List<KItem> = items.value.filter { it.author == author }

    /** الخطبة كاملة: من الحي (إن كانت في الفهرس الحي) ثم المضمّن. */
    suspend fun get(id: String): Khutba? {
        cache[id]?.let { return it }
        val it = item(id) ?: return null
        val path = it.file.ifEmpty { "${it.author}/${it.id}.json" }
        var k: Khutba? = null
        if (id in remoteIds) {
            k = runCatching { Api.json.decodeFromString<Khutba>(Api.getAbs("$REMOTE/$path")) }.getOrNull()
        }
        if (k == null) k = assetText(path)?.let { runCatching { Api.json.decodeFromString<Khutba>(it) }.getOrNull() }
        if (k != null) cache[id] = k
        return k
    }
}
