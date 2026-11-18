package mx.habla

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Busca la última versión publicada en GitHub Releases y ofrece instalarla. */
object Updater {

    fun check(activity: Activity) {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isBlank()) return
        thread {
            try {
                val json = JSONObject(get("https://api.github.com/repos/$repo/releases/latest"))
                val remote = json.getString("tag_name").removePrefix("v").toIntOrNull() ?: return@thread
                if (remote <= BuildConfig.VERSION_CODE) return@thread
                val assets = json.getJSONArray("assets")
                var url: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
                }
                if (url == null) return@thread
                activity.runOnUiThread { if (!activity.isFinishing) ask(activity, url) }
            } catch (_: Exception) {
                // Sin internet o sin versión nueva: no pasa nada.
            }
        }
    }

    private fun ask(activity: Activity, url: String) {
        AlertDialog.Builder(activity)
            .setTitle("Hay una versión nueva 💗")
            .setMessage("¿Quieres actualizar Habla ahora?")
            .setPositiveButton("Actualizar") { _, _ -> download(activity, url) }
            .setNegativeButton("Después", null)
            .show()
    }

    private fun download(activity: Activity, url: String) {
        // Android pide permitir una sola vez "instalar apps desconocidas" para Habla.
        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return
        }
        val dialog = AlertDialog.Builder(activity).setMessage("Descargando…").setCancelable(false).show()
        thread {
            try {
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                val file = File(dir, "habla.apk")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.inputStream.use { i -> file.outputStream().use { o -> i.copyTo(o) } }
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
                activity.runOnUiThread {
                    dialog.dismiss()
                    activity.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    dialog.dismiss()
                    AlertDialog.Builder(activity).setMessage("No se pudo descargar. Intenta más tarde.")
                        .setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}
