package com.netrik.core.portscan

import android.content.Context
import com.netrik.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Reader
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Listas Top 100/1000 (ranking do Nmap, só números) e nomes de serviço (registro IANA). */
interface PortCatalog {
    suspend fun top(protocol: Protocol, count: Int): List<Int>
    suspend fun serviceNames(protocol: Protocol): Map<Int, String>
}

/** Leitura pura dos arquivos de assets; testada contra os arquivos reais. */
object PortCatalogFiles {
    fun parseTop(reader: Reader): List<Int> = reader.readLines().mapNotNull { it.trim().toIntOrNull() }

    fun parseServices(reader: Reader): Map<Protocol, Map<Int, String>> {
        val tcp = HashMap<Int, String>()
        val udp = HashMap<Int, String>()
        reader.forEachLine { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@forEachLine
            val port = parts[1].toIntOrNull() ?: return@forEachLine
            when (parts[0]) {
                "tcp" -> tcp[port] = parts[2]
                "udp" -> udp[port] = parts[2]
            }
        }
        return mapOf(Protocol.Tcp to tcp, Protocol.Udp to udp)
    }
}

@Singleton
class AssetPortCatalog @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : PortCatalog {

    private val lock = Mutex()
    private var tops: Map<Protocol, List<Int>>? = null
    private var names: Map<Protocol, Map<Int, String>>? = null

    override suspend fun top(protocol: Protocol, count: Int): List<Int> = load().first.getValue(protocol).take(count)

    override suspend fun serviceNames(protocol: Protocol): Map<Int, String> = load().second.getValue(protocol)

    private suspend fun load(): Pair<Map<Protocol, List<Int>>, Map<Protocol, Map<Int, String>>> = lock.withLock {
        val t = tops
        val n = names
        if (t != null && n != null) return@withLock t to n
        withContext(io) {
            val loadedTops = mapOf(
                Protocol.Tcp to context.assets.open("ports/top-tcp.txt").bufferedReader().use(PortCatalogFiles::parseTop),
                Protocol.Udp to context.assets.open("ports/top-udp.txt").bufferedReader().use(PortCatalogFiles::parseTop),
            )
            val loadedNames = GZIPInputStream(context.assets.open("ports/services.tsv.gzip")).bufferedReader().use(PortCatalogFiles::parseServices)
            tops = loadedTops
            names = loadedNames
            loadedTops to loadedNames
        }
    }
}
