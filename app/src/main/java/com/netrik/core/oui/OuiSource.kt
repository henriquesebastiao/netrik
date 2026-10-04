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

/** Origem dos arquivos OUI: a cópia embarcada no APK e os arquivos públicos do IEEE. */
interface OuiSource {
    /** Data dos dados embarcados (ISO-8601). */
    fun bundledVersion(): String

    fun openBundled(registry: OuiRegistry): Reader

    /**
     * Baixa o CSV do [registry] para um arquivo temporário. Bloqueante: chamar fora da main thread.
     * [onProgress] recebe bytes lidos e o total (-1 se desconhecido).
     */
    fun download(registry: OuiRegistry, onProgress: (read: Long, total: Long) -> Unit): File
}

class HttpStatusException(val code: Int) : IOException("HTTP $code")

class AndroidOuiSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OuiSource {

    override fun bundledVersion(): String =
        context.assets.open("$ASSET_DIR/VERSION").bufferedReader().use { it.readText().trim() }

    // Extensão ".gzip" de propósito: o AGP descompacta assets ".gz" no build e remove a extensão.
    override fun openBundled(registry: OuiRegistry): Reader =
        GZIPInputStream(context.assets.open("$ASSET_DIR/${registry.fileName}.csv.gzip")).bufferedReader(Charsets.UTF_8)

    override fun download(registry: OuiRegistry, onProgress: (Long, Long) -> Unit): File {
        val target = File.createTempFile("oui-${registry.fileName}", ".csv", context.cacheDir)
        val connection = URL(registry.ieeeUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            // O IEEE rejeita (HTTP 418) o User-Agent padrão do Android ("Dalvik/…").
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
