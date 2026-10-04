package com.netrik.core.ssh

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.netrik.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Lê o arquivo de chave escolhido no seletor do sistema (Storage Access Framework). */
class KeyFileReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    sealed interface Result {
        class Read(val name: String, val bytes: ByteArray) : Result
        data object TooLarge : Result
        data object Unreadable : Result
    }

    suspend fun read(uri: Uri): Result = withContext(io) {
        try {
            val name = displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "chave"
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                // Lê um byte além do limite só para saber se passou dele.
                input.readNBytesCompat(PrivateKeys.MAX_SIZE + 1)
            } ?: return@withContext Result.Unreadable
            if (bytes.size > PrivateKeys.MAX_SIZE) Result.TooLarge else Result.Read(name, bytes)
        } catch (_: IOException) {
            Result.Unreadable
        } catch (_: SecurityException) {
            Result.Unreadable
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
