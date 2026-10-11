package com.irth.mirath.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object Api {
    const val BASE = "https://medmrf-10.github.io/irth"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    val json = Json { ignoreUnknownKeys = true }

    private var cacheDir: File? = null

    fun init(ctx: Context) {
        if (cacheDir == null) cacheDir = File(ctx.cacheDir, "http").apply { mkdirs() }
    }

    private fun cacheFile(path: String): File? =
        cacheDir?.let { File(it, path.hashCode().toString(16) + ".cache") }

    /** جلب نص مع كاش قرصي: عند فشل الشبكة يُقدَّم آخر نسخة ناجحة. */
    suspend fun get(path: String, cache: Boolean = true): String = withContext(Dispatchers.IO) {
        val cf = cacheFile(path)
        try {
            val req = Request.Builder().url("$BASE$path").build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code} on $path")
                val body = r.body?.string() ?: ""
                if (cache) runCatching { cf?.writeText(body) }
                body
            }
        } catch (e: Exception) {
            val cached = cf?.takeIf { it.exists() }?.readText()
            if (cached != null) cached else throw e
        }
    }

    /** جلب من رابط مطلق (مصادر خارج BASE مثل hawshat raw) بنفس الكاش. */
    suspend fun getAbs(url: String, cache: Boolean = true): String = withContext(Dispatchers.IO) {
        val cf = cacheFile(url)
        try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code} on $url")
                val body = r.body?.string() ?: ""
                if (cache) runCatching { cf?.writeText(body) }
                body
            }
        } catch (e: Exception) {
            val cached = cf?.takeIf { it.exists() }?.readText()
            if (cached != null) cached else throw e
        }
    }

    suspend fun getJson(path: String, cache: Boolean = true): JsonElement = json.parseToJsonElement(get(path, cache)).jsonObject
    suspend fun getText(path: String, cache: Boolean = true): String = get(path, cache)

    inline fun <reified T> decode(el: JsonElement): T = json.decodeFromJsonElement(el)
}
