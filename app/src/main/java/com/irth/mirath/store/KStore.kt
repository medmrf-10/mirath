package com.irth.mirath.store

import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** فقرة محفوظة: نسخة كاملة (عنوان + نص) كي تُقرأ بلا اتصال وبلا تحميل الخطبة. */
@Serializable
data class SavedBlock(
    val k: String, val b: String, val h: String, val t: String,
    val kt: String = "", val au: String = "", val ts: Long = 0,
)

@Serializable
data class KFolder(val id: String, val name: String, val parent: String = "")

@Serializable
data class MixBlock(val k: String, val b: String, val h: String, val t: String, val kt: String = "")

/** خطبة مخلوطة: تبدأ فارغة وتُجمع فقراتها من خطب مختلفة. */
@Serializable
data class KMix(
    val id: String, val title: String, val folder: String = "",
    val blocks: List<MixBlock> = emptyList(), val ts: Long = 0,
)

@Serializable
data class KData(
    val saved: List<SavedBlock> = emptyList(),
    val folders: List<KFolder> = emptyList(),
    val mixes: List<KMix> = emptyList(),
)

/** مخزن المحفوظات والمجلدات والخطب المخلوطة — يُحفظ JSON واحداً داخل Prefs (فيدخل في النسخ الاحتياطي). */
object KStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val KEY = "kstore"

    val data = mutableStateOf(KData())

    fun init() = reload()

    /** إعادة القراءة من Prefs (بعد الاستعادة من نسخة احتياطية). */
    fun reload() {
        data.value = runCatching { json.decodeFromString<KData>(Prefs.str(KEY).ifEmpty { "{}" }) }.getOrDefault(KData())
    }

    private fun commit(d: KData) {
        data.value = d
        Prefs.putStr(KEY, json.encodeToString(d))
    }

    private fun newId(): String = System.currentTimeMillis().toString(36) + (0..999).random().toString(36)

    // ---- المحفوظات
    fun isSaved(blockId: String): Boolean = data.value.saved.any { it.b == blockId }

    fun savedCount(khutbaId: String): Int = data.value.saved.count { it.k == khutbaId }

    /** يحفظ الفقرة إن لم تكن محفوظة، ويزيلها إن كانت؛ يعيد true إن صارت محفوظة. */
    fun toggleSave(s: SavedBlock): Boolean {
        val d = data.value
        return if (d.saved.any { it.b == s.b }) {
            commit(d.copy(saved = d.saved.filterNot { it.b == s.b })); false
        } else {
            commit(d.copy(saved = listOf(s.copy(ts = System.currentTimeMillis())) + d.saved)); true
        }
    }

    // ---- المجلدات
    fun subfolders(parent: String): List<KFolder> = data.value.folders.filter { it.parent == parent }
    fun folder(id: String): KFolder? = data.value.folders.firstOrNull { it.id == id }
    fun mixesIn(folder: String): List<KMix> = data.value.mixes.filter { it.folder == folder }
    fun mix(id: String): KMix? = data.value.mixes.firstOrNull { it.id == id }

    fun newFolder(name: String, parent: String): String {
        val f = KFolder(newId(), name.trim().ifEmpty { "مجلد جديد" }, parent)
        commit(data.value.copy(folders = data.value.folders + f))
        return f.id
    }

    fun renameFolder(id: String, name: String) {
        if (name.isBlank()) return
        commit(data.value.copy(folders = data.value.folders.map { if (it.id == id) it.copy(name = name.trim()) else it }))
    }

    /** حذف مجلد مع مجلداته الفرعية؛ الخطب المخلوطة بداخله تنتقل إلى مجلده الأب (لا تضيع). */
    fun deleteFolder(id: String) {
        val d = data.value
        val target = d.folders.firstOrNull { it.id == id } ?: return
        val gone = mutableSetOf(id)
        var grew = true
        while (grew) {
            grew = false
            d.folders.forEach { if (it.parent in gone && gone.add(it.id)) grew = true }
        }
        commit(
            d.copy(
                folders = d.folders.filterNot { it.id in gone },
                mixes = d.mixes.map { if (it.folder in gone) it.copy(folder = target.parent) else it },
            )
        )
    }

    // ---- الخطب المخلوطة
    fun newMix(title: String, folder: String): String {
        val m = KMix(newId(), title.trim().ifEmpty { "خطبة مخلوطة" }, folder, ts = System.currentTimeMillis())
        commit(data.value.copy(mixes = data.value.mixes + m))
        return m.id
    }

    fun renameMix(id: String, title: String) {
        if (title.isBlank()) return
        commit(data.value.copy(mixes = data.value.mixes.map { if (it.id == id) it.copy(title = title.trim()) else it }))
    }

    fun deleteMix(id: String) = commit(data.value.copy(mixes = data.value.mixes.filterNot { it.id == id }))

    /** يضيف فقرة إلى آخر الخطبة المخلوطة؛ false إن كانت موجودة فيها سابقاً. */
    fun addToMix(mixId: String, b: MixBlock): Boolean {
        val d = data.value
        val m = d.mixes.firstOrNull { it.id == mixId } ?: return false
        if (m.blocks.any { it.b == b.b }) return false
        commit(d.copy(mixes = d.mixes.map { if (it.id == mixId) it.copy(blocks = it.blocks + b) else it }))
        return true
    }

    /** يعيد فقرة إلى موضع محدد في الخطبة المخلوطة (للتراجع عن الإزالة)؛ false إن كانت موجودة. */
    fun insertInMix(mixId: String, index: Int, b: MixBlock): Boolean {
        val d = data.value
        val m = d.mixes.firstOrNull { it.id == mixId } ?: return false
        if (m.blocks.any { it.b == b.b }) return false
        val l = m.blocks.toMutableList()
        l.add(index.coerceIn(0, l.size), b)
        commit(d.copy(mixes = d.mixes.map { if (it.id == mixId) it.copy(blocks = l) else it }))
        return true
    }

    fun removeFromMix(mixId: String, blockId: String) {
        val d = data.value
        commit(d.copy(mixes = d.mixes.map { if (it.id == mixId) it.copy(blocks = it.blocks.filterNot { x -> x.b == blockId }) else it }))
    }

    /** تحريك فقرة داخل الخطبة المخلوطة: dir = -1 للأعلى و+1 للأسفل. */
    fun moveInMix(mixId: String, blockId: String, dir: Int) {
        val d = data.value
        commit(
            d.copy(
                mixes = d.mixes.map { m ->
                    if (m.id != mixId) m else {
                        val l = m.blocks.toMutableList()
                        val i = l.indexOfFirst { it.b == blockId }
                        val j = i + dir
                        if (i >= 0 && j in l.indices) { val x = l[i]; l[i] = l[j]; l[j] = x }
                        m.copy(blocks = l)
                    }
                }
            )
        )
    }
}
