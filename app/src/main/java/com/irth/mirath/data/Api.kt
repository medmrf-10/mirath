package com.irth.mirath.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object Api {
    const val BASE = "https://medmrf-10.github.io/irth"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    val json = Json { ignoreUnknownKeys = true }

    suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$BASE$path").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code} on $path")
            r.body?.string() ?: ""
        }
    }

    suspend fun getJson(path: String): JsonElement = json.parseToJsonElement(get(path)).jsonObject
    suspend fun getText(path: String): String = get(path)

    inline fun <reified T> decode(el: JsonElement): T = json.decodeFromJsonElement(el)
}
