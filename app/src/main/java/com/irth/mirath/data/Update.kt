package com.irth.mirath.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** فحص تحديث ذاتي من كتالوج المتجر (apps.json) — يقارن vcode المثبّت بآخر منشور. */
object Update {
    private const val RAW_BASE = "https://raw.githubusercontent.com/medmrf-10/store/main"
    private const val PAGES_BASE = "https://medmrf-10.github.io/store"
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val sjson = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class AppE(val slug: String = "", val name: String = "", val url: String = "", val vcode: Int = 0, val version: String = "")

    data class Found(val version: String, val url: String)

    private fun fetchFrom(base: String, slug: String): AppE? = runCatching {
        val url = "$base/apps.json?t=${System.currentTimeMillis() / 15000}"
        val body = client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
        val apps = sjson.decodeFromString<Map<String, List<AppE>>>(body)["apps"] ?: return@runCatching null
        apps.firstOrNull { it.slug == slug }
    }.getOrNull()

    suspend fun check(slug: String, myVcode: Int): Found? = withContext(Dispatchers.IO) {
        val rawMe = fetchFrom(RAW_BASE, slug)
        val pagesMe = fetchFrom(PAGES_BASE, slug)
        val me = listOfNotNull(rawMe, pagesMe).maxByOrNull { it.vcode } ?: return@withContext null
        val chosenBase = if (rawMe != null && rawMe.vcode >= me.vcode) RAW_BASE else PAGES_BASE
        if (me.vcode > myVcode && me.url.isNotEmpty()) {
            Found(
                me.version,
                if (me.url.startsWith("http")) me.url else "$chosenBase/${me.url.removePrefix("./").removePrefix("/")}",
            )
        } else null
    }

    fun download(ctx: Context, f: Found, slug: String) {
        val file = "$slug-${f.version}.apk"
        val req = DownloadManager.Request(Uri.parse(f.url))
            .setTitle("تحديث $slug ${f.version}")
            .setDescription(file)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file)
            .setMimeType("application/vnd.android.package-archive")
        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
    }
}

class DlReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (id == -1L) return
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = dm.query(DownloadManager.Query().setFilterById(id))
        if (!q.moveToFirst()) return
        if (q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
            val uri = dm.getUriForDownloadedFile(id) ?: return
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }
}
