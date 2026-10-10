package com.irth.mirath.data

import android.content.Context
import com.irth.mirath.store.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * مزامنة ملفات المستخدم مع GitHub عبر Device Flow (لا كلمة سر ولا سر داخل التطبيق):
 * startAuth يطلب رمزاً من GitHub → المستخدم يدخله في المتصفح → pollToken يرجع التوكن.
 * بعد الدخول: push يرفع imports/ + roadmaps.json + prefs.json لمسار المستخدم في الريبو، pull يستعيدها.
 */
object Sync {
    /** معرف تطبيق OAuth — يُملأ عند إنشاء التطبيق في GitHub. */
    var clientId: String = ""
    var repo: String = "medmrf-10/mirath-data"
    var scope: String = "repo"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    private val JSONT = "application/json".toMediaType()
    private val FORMT = "application/x-www-form-urlencoded".toMediaType()

    fun token(): String = Prefs.str("gh.token")
    fun signedIn(): Boolean = token().isNotEmpty()
    fun signOut() = Prefs.putStr("gh.token", "")

    data class DeviceCode(val userCode: String, val verifyUrl: String, val deviceCode: String, val interval: Int)

    suspend fun startAuth(): DeviceCode? = withContext(Dispatchers.IO) {
        if (clientId.isEmpty()) return@withContext null
        runCatching {
            val body = "client_id=$clientId&scope=$scope".toRequestBody(FORMT)
            val req = Request.Builder().url("https://github.com/login/device/code")
                .header("Accept", "application/json").post(body).build()
            val o = Api.json.parseToJsonElement(client.newCall(req).execute().body!!.string()).jsonObject
            DeviceCode(
                o["user_code"]!!.jsonPrimitive.content,
                o["verification_uri"]!!.jsonPrimitive.content,
                o["device_code"]!!.jsonPrimitive.content,
                o["interval"]!!.jsonPrimitive.content.toInt(),
            )
        }.getOrNull()
    }

    /** استطلاع حتى يدخل المستخدم الرمز — يرجع التوكن أو null عند الانتهاء/الرفض. */
    suspend fun pollToken(dc: DeviceCode, maxSecs: Int = 600): String? = withContext(Dispatchers.IO) {
        val till = System.currentTimeMillis() + maxSecs * 1000L
        var wait = dc.interval.coerceIn(3, 15)
        while (System.currentTimeMillis() < till) {
            val res = runCatching {
                val body = ("client_id=$clientId&device_code=${dc.deviceCode}" +
                    "&grant_type=urn:ietf:params:oauth:grant-type:device_code").toRequestBody(FORMT)
                val req = Request.Builder().url("https://github.com/login/oauth/access_token")
                    .header("Accept", "application/json").post(body).build()
                Api.json.parseToJsonElement(client.newCall(req).execute().body!!.string()).jsonObject
            }.getOrNull() ?: break
            res["access_token"]?.jsonPrimitive?.content?.let { Prefs.putStr("gh.token", it); return@withContext it }
            when (res["error"]?.jsonPrimitive?.content) {
                "authorization_pending" -> delay(wait * 1000L)
                "slow_down" -> { wait += 5; delay(wait * 1000L) }
                else -> return@withContext null
            }
        }
        null
    }

    private fun ghReq(method: String, path: String, body: String?): Request {
        val b = Request.Builder().url("https://api.github.com$path")
            .header("Authorization", "Bearer ${token()}")
            .header("Accept", "application/vnd.github+json")
        return when (method) { "GET" -> b.get(); else -> b.method(method, (body ?: "").toRequestBody(JSONT)) }.build()
    }

    private fun userRoot(ctx: Context): String =
        "users/" + Prefs.str("gh.user").ifEmpty { "me" }

    private suspend fun ghUser(): String = withContext(Dispatchers.IO) {
        runCatching {
            val o = Api.json.parseToJsonElement(client.newCall(ghReq("GET", "/user", null)).execute().body!!.string()).jsonObject
            o["login"]!!.jsonPrimitive.content
        }.getOrDefault("me")
    }

    /** رفع ملف واحد للريبو (contents API — ينشئ أو يحدّث). */
    private suspend fun putFile(path: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val sha = runCatching {
                val o = Api.json.parseToJsonElement(client.newCall(ghReq("GET", "/repos/$repo/contents/$path", null)).execute().body!!.string()).jsonObject
                o["sha"]?.jsonPrimitive?.content
            }.getOrNull()
            val payload = buildString {
                append("{\"message\":\"mirath sync\",\"content\":\"")
                append(Base64.getEncoder().encodeToString(bytes))
                append("\"")
                sha?.let { append(",\"sha\":\"").append(it).append("\"") }
                append("}")
            }
            client.newCall(ghReq("PUT", "/repos/$repo/contents/$path", payload)).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** تنزيل ملف من الريبو إن وُجد. */
    private suspend fun getFile(path: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(ghReq("GET", "/repos/$repo/contents/$path", null)).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val o = Api.json.parseToJsonElement(r.body!!.string()).jsonObject
                Base64.getDecoder().decode(o["content"]!!.jsonPrimitive.content.replace("\n", ""))
            }
        }.getOrNull()
    }

    /** رفع كل ملفات المستخدم: imports/ + roadmaps.json + prefs.json → users/<me>/… يرجع عدد المرفوع. */
    suspend fun push(ctx: Context): Int = withContext(Dispatchers.IO) {
        if (token().isEmpty()) return@withContext -1
        val root = File(ctx.filesDir, "imports")
        Prefs.putStr("gh.user", ghUser())
        val base = userRoot(ctx)
        var n = 0
        root.walkTopDown().filter { it.isFile }.forEach { f ->
            if (putFile("$base/imports/${f.relativeTo(root).path}", f.readBytes())) n++
        }
        File(ctx.filesDir, "roadmaps.json").takeIf { it.isFile }?.let {
            if (putFile("$base/roadmaps.json", it.readBytes())) n++
        }
        if (putFile("$base/prefs.json", Prefs.exportAll().toByteArray())) n++
        n
    }

    /** تنزيل كل ملفات المستخدم من الريبو → imports/ + roadmaps + prefs. يرجع عدد المستعاد. */
    suspend fun pull(ctx: Context): Int = withContext(Dispatchers.IO) {
        if (token().isEmpty()) return@withContext -1
        val base = userRoot(ctx)
        var n = 0
        // قائمة الملفات عبر git trees API (recursive)
        val paths = runCatching {
            val tr = Api.json.parseToJsonElement(
                client.newCall(ghReq("GET", "/repos/$repo/git/trees/HEAD?recursive=1", null)).execute().body!!.string()
            ).jsonObject["tree"]
            tr?.let { Api.decode<List<kotlinx.serialization.json.JsonObject>>(it) }
                ?.mapNotNull { it["path"]?.jsonPrimitive?.content }
                ?.filter { it.startsWith("$base/") } ?: emptyList()
        }.getOrDefault(emptyList())
        paths.forEach { p ->
            getFile(p)?.let { bytes ->
                val rel = p.removePrefix("$base/")
                when {
                    rel.startsWith("imports/") -> {
                        val out = File(ctx.filesDir, rel)
                        out.parentFile?.mkdirs(); out.writeBytes(bytes); n++
                    }
                    rel == "roadmaps.json" -> { File(ctx.filesDir, "roadmaps.json").writeBytes(bytes); n++ }
                    rel == "prefs.json" -> { Prefs.importAll(String(bytes)); n++ }
                }
            }
        }
        Repo.mergeLocal()
        n
    }
}
