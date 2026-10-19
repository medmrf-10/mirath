package com.irth.mirath.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object Api {
    const val BASE = "https://raw.githubusercontent.com/medmrf-10/ytb/main"
    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private var cacheDir: File? = null

    fun init(ctx: Context) {
        if (cacheDir == null) cacheDir = File(ctx.filesDir, "cache").apply { mkdirs() }
    }

    private fun key(path: String): String =
        MessageDigest.getInstance("SHA-1").digest(path.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun cachedFile(url: String): File? = cacheDir?.let { File(it, key(url)) }?.takeIf { it.exists() && it.length() > 0 }

    suspend fun getText(path: String, cache: Boolean = true): String = getAbs("$BASE$path", cache)

    /** جلب أي رابط مطلق مع كاش القرص وخيار التحديث المباشر. */
    suspend fun getAbs(url: String, cache: Boolean = true, forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        val f = if (cache) cacheDir?.let { File(it, key(url)) } else null
        if (!forceRefresh && f != null && f.exists() && f.length() > 0) {
            return@withContext f.readText()
        }
        val req = Request.Builder().url(url).build()
        try {
            val body = client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}: $url")
                r.body?.string() ?: ""
            }
            if (body.isNotEmpty()) {
                f?.writeText(body)
            }
            body
        } catch (e: Exception) {
            // إذا فشل الاتصال بالشبكة وكان لدينا كاش سابق على القرص، نرجعه فوراً
            if (f != null && f.exists() && f.length() > 0) return@withContext f.readText()
            throw e
        }
    }

    suspend fun getJson(path: String, cache: Boolean = true): JsonElement =
        json.parseToJsonElement(getText(path, cache))

    inline fun <reified T> decode(el: JsonElement): T =
        json.decodeFromJsonElement(kotlinx.serialization.serializer(), el)
}
