package com.netrik.core.knock

import android.content.Context
import android.net.Uri
import com.netrik.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Reads and writes the export files picked by the user (Storage Access Framework). */
class KnockFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Writes [text] to [uri]; false if it couldn't be written. */
    suspend fun write(uri: Uri, text: String): Boolean = withContext(io) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** File content, or null if unreadable or larger than [KnockTransfer.MAX_FILE_BYTES]. */
    suspend fun read(uri: Uri): String? = withContext(io) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > KnockTransfer.MAX_FILE_BYTES) return@use null
                }
                out.toByteArray().decodeToString()
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}
