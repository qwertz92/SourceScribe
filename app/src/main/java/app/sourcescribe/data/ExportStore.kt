package app.sourcescribe.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.core.ExportFormat
import app.sourcescribe.core.ExportState
import app.sourcescribe.core.TranscriptDocument
import app.sourcescribe.core.TranscriptExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Writes an already committed artifact to a user-selected SAF tree. */
@Singleton
class ExportStore @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: SourceScribeDao,
    private val artifacts: ArtifactFiles,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    suspend fun export(artifactId: String, format: ExportFormat, treeUri: String): ExportRow {
        val document = artifacts.read(artifactId)
        val id = UUID.randomUUID().toString()
        val row = ExportRow(
            id = id,
            artifactId = artifactId,
            format = format.name,
            treeUri = treeUri,
            createdAt = System.currentTimeMillis(),
        )
        dao.insertExport(row)
        return write(row, document)
    }

    suspend fun retry(exportId: String, newTreeUri: String? = null): ExportRow {
        val previous = dao.export(exportId) ?: throw IllegalArgumentException("export not found")
        val format = try {
            ExportFormat.valueOf(previous.format)
        } catch (failure: IllegalArgumentException) {
            throw IllegalArgumentException("stored export format is invalid", failure)
        }
        return export(previous.artifactId, format, newTreeUri ?: previous.treeUri)
    }

    /** Reconciles an export after process loss or an external target change. */
    suspend fun reconcile(exportId: String): ExportRow {
        val row = dao.export(exportId) ?: throw IllegalArgumentException("export not found")
        return when (row.state) {
            ExportState.PENDING, ExportState.WRITING ->
                updateAfterFailure(row, ExportState.FAILED, ERROR_EXPORT_INTERRUPTED, row.documentUri)

            ExportState.EXPORTED -> {
                val documentUri = row.documentUri
                if (documentUri == null) {
                    updateAfterFailure(row, ExportState.FAILED, ERROR_EXTERNAL_DOCUMENT_MISSING, null)
                } else {
                    try {
                        if (documentExists(documentUri.toUri())) {
                            row
                        } else {
                            updateAfterFailure(
                                row,
                                ExportState.FAILED,
                                ERROR_EXTERNAL_DOCUMENT_MISSING,
                                documentUri,
                            )
                        }
                    } catch (_: SecurityException) {
                        updateAfterFailure(row, ExportState.PERMISSION_REQUIRED, ERROR_PERMISSION_REQUIRED, documentUri)
                    } catch (_: IllegalArgumentException) {
                        updateAfterFailure(row, ExportState.FAILED, ERROR_EXTERNAL_DOCUMENT_MISSING, documentUri)
                    }
                }
            }

            else -> row
        }
    }

    private fun documentExists(uri: Uri): Boolean = resolver.query(
        uri,
        arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID),
        null,
        null,
        null,
    )?.use { cursor -> cursor.moveToFirst() } ?: false

    private suspend fun write(row: ExportRow, document: TranscriptDocument): ExportRow {
        var documentUri: String? = null
        try {
            val format = ExportFormat.valueOf(row.format)
            val payload = payload(document, format)
            val tree = row.treeUri.toUri()
            require(DocumentsContract.isTreeUri(tree)) { "export target is not a tree URI" }
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val isDirectory = resolver.query(parent, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use {
                it.moveToFirst() && it.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR
            } ?: false
            if (!isDirectory) throw IOException("export tree is not a directory")

            val chosenName = dao.artifact(row.artifactId)?.displayName
            val fileName = collisionSafeFileName(document, format, row.id, payload.extension, chosenName)
            val createdDocument = DocumentsContract.createDocument(resolver, parent, payload.mimeType, fileName)
                ?: throw IOException("export document could not be created")
            documentUri = createdDocument.toString()
            val writing = row.copy(state = ExportState.WRITING, documentUri = documentUri, error = null, verification = null)
            dao.updateExport(writing)

            val written = resolver.openOutputStream(createdDocument, "w")
                ?: throw IOException("export document could not be opened")
            val digest = writePayload(payload, written)
            val verification = verifyReadback(createdDocument, digest)
            if (verification == VERIFICATION_MISMATCH) throw IOException(VERIFICATION_MISMATCH)

            val complete = writing.copy(
                state = ExportState.EXPORTED,
                verification = verification,
                error = null,
            )
            dao.updateExport(complete)
            return complete
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                val remainingUri = cleanup(documentUri)
                updateAfterFailure(row, ExportState.FAILED, ERROR_CANCELLED, remainingUri)
            }
            throw cancelled
        } catch (security: SecurityException) {
            val remainingUri = cleanup(documentUri)
            return updateAfterFailure(row, ExportState.PERMISSION_REQUIRED, ERROR_PERMISSION_REQUIRED, remainingUri)
        } catch (failure: IOException) {
            val remainingUri = cleanup(documentUri)
            return updateAfterFailure(row, ExportState.FAILED, failureCategory(failure), remainingUri)
        } catch (failure: IllegalArgumentException) {
            val remainingUri = cleanup(documentUri)
            return updateAfterFailure(row, ExportState.FAILED, failureCategory(failure), remainingUri)
        }
    }

    private fun payload(document: TranscriptDocument, format: ExportFormat): Payload {
        if (format == ExportFormat.RAW) {
            val raw = artifacts.rawFile(document.artifactId)
                ?: throw IOException(ERROR_RAW_NOT_RETAINED)
            val extension = rawExtension(raw)
            return Payload(
                extension = extension,
                mimeType = mimeType(format, extension),
                maxBytes = ArtifactFiles.MAX_RAW_BYTES.toLong(),
                open = raw::inputStream,
            )
        }
        if (!TranscriptExporter.supports(document, format)) {
            throw IllegalArgumentException("format is not supported by artifact")
        }
        val bytes = TranscriptExporter.render(document, format).toByteArray(StandardCharsets.UTF_8)
        return Payload(
            extension = extension(format),
            mimeType = mimeType(format, extension(format)),
            maxBytes = MAX_RENDERED_BYTES,
            open = { ByteArrayInputStream(bytes) },
        )
    }

    private fun writePayload(payload: Payload, output: OutputStream): DigestAndSize {
        output.use { target ->
            payload.open().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                var bytes = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    if (bytes > payload.maxBytes - read) throw IOException(ERROR_TOO_LARGE)
                    target.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    bytes += read
                }
                target.flush()
                return DigestAndSize(hash(digest.digest()), bytes)
            }
        }
    }

    private fun verifyReadback(uri: Uri, expected: DigestAndSize): String {
        val input = try {
            resolver.openInputStream(uri) ?: return VERIFICATION_UNVERIFIED
        } catch (_: IOException) {
            return VERIFICATION_UNVERIFIED
        } catch (_: SecurityException) {
            // A provider can permit writing without permitting a verification read.
            return VERIFICATION_UNVERIFIED
        }
        return try {
            input.use { source ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                var bytes = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    if (bytes > MAX_READBACK_BYTES - read) return VERIFICATION_MISMATCH
                    digest.update(buffer, 0, read)
                    bytes += read
                }
                if (bytes == expected.bytes && hash(digest.digest()) == expected.sha256) {
                    VERIFICATION_VERIFIED
                } else {
                    VERIFICATION_MISMATCH
                }
            }
        } catch (_: IOException) {
            VERIFICATION_UNVERIFIED
        } catch (_: SecurityException) {
            VERIFICATION_UNVERIFIED
        }
    }

    private suspend fun updateAfterFailure(
        row: ExportRow,
        state: ExportState,
        error: String,
        documentUri: String?,
    ): ExportRow {
        val failed = row.copy(state = state, documentUri = documentUri, verification = null, error = error)
        dao.updateExport(failed)
        return failed
    }

    private fun cleanup(uri: String?): String? {
        if (uri == null) return null
        return try {
            if (DocumentsContract.deleteDocument(resolver, uri.toUri())) null else uri
        } catch (_: IOException) {
            uri
        } catch (_: SecurityException) {
            uri
        } catch (_: RuntimeException) {
            uri
        }
    }

    private data class Payload(
        val extension: String,
        val mimeType: String,
        val maxBytes: Long,
        val open: () -> InputStream,
    )

    private data class DigestAndSize(val sha256: String, val bytes: Long)

    companion object {
        private const val COPY_BUFFER_BYTES = 16 * 1024
        private const val MAX_RENDERED_BYTES = 32L * 1024 * 1024
        private const val MAX_READBACK_BYTES = MAX_RENDERED_BYTES
        private const val VERIFICATION_VERIFIED = "VERIFIED"
        private const val VERIFICATION_UNVERIFIED = "UNVERIFIED"
        private const val VERIFICATION_MISMATCH = "MISMATCH"
        private const val ERROR_CANCELLED = "CANCELLED"
        private const val ERROR_PERMISSION_REQUIRED = "PERMISSION_REQUIRED"
        private const val ERROR_EXTERNAL_DOCUMENT_MISSING = "EXTERNAL_DOCUMENT_MISSING"
        private const val ERROR_EXPORT_INTERRUPTED = "EXPORT_INTERRUPTED"
        private const val ERROR_RAW_NOT_RETAINED = "RAW_NOT_RETAINED"
        private const val ERROR_TOO_LARGE = "TOO_LARGE"

        /**
         * A chosen name is written as chosen; the storage layer separates a repeat of the same name.
         * A generated name carries a per-export discriminator so two exports never collide silently.
         */
        internal fun collisionSafeFileName(
            document: TranscriptDocument,
            format: ExportFormat,
            exportId: String,
            rawExtension: String? = null,
            override: String? = null,
        ): String = TranscriptExporter.fileName(
            document = document,
            format = format,
            override = override,
            discriminator = exportId,
            rawExtension = rawExtension,
        )

        private fun extension(format: ExportFormat): String = when (format) {
            ExportFormat.MARKDOWN -> "md"
            ExportFormat.TEXT -> "txt"
            ExportFormat.JSON -> "json"
            ExportFormat.SRT -> "srt"
            ExportFormat.VTT -> "vtt"
            ExportFormat.RAW -> error("RAW requires retained metadata")
        }

        private fun mimeType(format: ExportFormat, rawExtension: String): String = when (format) {
            ExportFormat.MARKDOWN -> "text/markdown"
            ExportFormat.TEXT -> "text/plain"
            ExportFormat.JSON -> "application/json"
            ExportFormat.SRT -> "application/x-subrip"
            ExportFormat.VTT -> "text/vtt"
            ExportFormat.RAW -> when (rawExtension) {
                "zip" -> "application/zip"
                "json", "json3" -> "application/json"
                "srt" -> "application/x-subrip"
                "vtt" -> "text/vtt"
                else -> "text/plain"
            }
        }

        private fun rawExtension(raw: File): String {
            val name = raw.name
            if (!name.startsWith("raw.")) throw IOException(ERROR_RAW_NOT_RETAINED)
            val extension = name.removePrefix("raw.")
            if (extension !in setOf("srt", "vtt", "json3", "json", "txt", "zip")) {
                throw IOException(ERROR_RAW_NOT_RETAINED)
            }
            return extension
        }

        private fun hash(bytes: ByteArray): String = buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                append(HEX[(byte.toInt() ushr 4) and 0x0f])
                append(HEX[byte.toInt() and 0x0f])
            }
        }

        private val HEX = "0123456789abcdef"

        private fun failureCategory(failure: Exception): String = when (failure) {
            is IOException -> failure.message?.takeIf { it in setOf(ERROR_RAW_NOT_RETAINED, ERROR_TOO_LARGE, VERIFICATION_MISMATCH) }
                ?: "IO_FAILURE"
            else -> "INVALID_EXPORT"
        }
    }
}
