package com.netrik.core.oui

import android.content.Context
import com.netrik.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import javax.inject.Inject

/** Source of the OUI files: the copy bundled in the APK and the IEEE public files. */
interface OuiSource {
    /** Date of the bundled data (ISO-8601). */
    fun bundledVersion(): String

    fun openBundled(registry: OuiRegistry): Reader

    /**
     * Downloads the CSV of [registry] to a temporary file. Blocking: call it off the main thread.
     * [onProgress] receives the bytes read and the total (-1 if unknown).
     */
    fun download(registry: OuiRegistry, onProgress: (read: Long, total: Long) -> Unit): File
}

class HttpStatusException(val code: Int) : IOException("HTTP $code")

class AndroidOuiSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OuiSource {

    override fun bundledVersion(): String =
        context.assets.open("$ASSET_DIR/VERSION").bufferedReader().use { it.readText().trim() }

    // ".gzip" extension on purpose: AGP decompresses ".gz" assets at build time and drops the extension.
    override fun openBundled(registry: OuiRegistry): Reader =
        GZIPInputStream(context.assets.open("$ASSET_DIR/${registry.fileName}.csv.gzip")).bufferedReader(Charsets.UTF_8)

    override fun download(registry: OuiRegistry, onProgress: (Long, Long) -> Unit): File {
        val target = File.createTempFile("oui-${registry.fileName}", ".csv", context.cacheDir)
        val connection = URL(registry.ieeeUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            // The IEEE rejects (HTTP 418) Android's default User-Agent ("Dalvik/…").
            connection.setRequestProperty("User-Agent", "Netrik/${BuildConfig.VERSION_NAME} (Android)")
            connection.setRequestProperty("Accept", "text/csv")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw HttpStatusException(code)
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw IOException("Cancelado")
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
            return target
        } catch (e: IOException) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private val OuiRegistry.fileName: String
        get() = label.lowercase()

    private val OuiRegistry.ieeeUrl: String
        get() = when (this) {
            OuiRegistry.MaL -> "https://standards-oui.ieee.org/oui/oui.csv"
            OuiRegistry.MaM -> "https://standards-oui.ieee.org/oui28/mam.csv"
            OuiRegistry.MaS -> "https://standards-oui.ieee.org/oui36/oui36.csv"
        }

    private companion object {
        const val ASSET_DIR = "oui"
    }
}
