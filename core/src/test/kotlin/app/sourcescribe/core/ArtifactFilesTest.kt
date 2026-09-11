package app.sourcescribe.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ArtifactFilesTest {
    @Test
    fun opaqueRawZipIsRetainedByteForByte() = withStore { store ->
        val raw = java.io.ByteArrayOutputStream().also { bytes ->
            java.util.zip.ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("response-0.json"))
                zip.write("{ \"text\": \"original\", \"unknown\": true }".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val retained = document(acquisition = JobConfig(retainRaw = true))
        store.write(retained, raw, "zip")
        assertArrayEquals(raw, requireNotNull(store.rawFile(retained.artifactId)).readBytes())
        assertEquals(retained, store.read(retained.artifactId))
    }

    @Test
    fun writeIsIdempotentAndConflictsStayImmutable() = withStore { store ->
        val document = document()

        val first = store.write(document)
        val second = store.write(document)

        assertEquals(first, second)
        assertEquals(first.bytes, store.canonicalFile(document.artifactId).length())
        assertEquals(listOf(first), store.recoverable())

        val conflict = assertThrows(ArtifactFilesException::class.java) {
            store.write(document.copy(warnings = listOf("different content")))
        }
        assertEquals(ArtifactFilesException.CONFLICTING_CONTENT, conflict.reason)
        assertEquals(document, store.read(document.artifactId))
    }

    @Test
    fun rawRoundTripRetainsFullProvenanceAndControlledExtension() = withStore { store ->
        val document = document(
            acquisition = JobConfig(retainRaw = true, provider = Provider.OPENAI, model = "requested"),
            provenance = Provenance(
                origin = Origin.PROVIDER,
                generation = Generation.AUTOMATIC,
                translation = Translation.NONE,
                provider = Provider.OPENAI,
                requestedModel = "requested",
                reportedModel = "reported",
                languageEvidence = "provider response",
                engineVersions = mapOf("engine" to "1"),
            ),
        )
        val raw = "raw provider response\nwith data".toByteArray()

        val stored = store.write(document, raw, "srt")

        assertEquals("srt", stored.rawExtension)
        assertArrayEquals(raw, store.rawFile(document.artifactId)!!.readBytes())
        assertEquals(document, store.read(document.artifactId))
        assertEquals(listOf(stored), store.recoverable())
    }

    @Test
    fun retentionEnabledRequiresRawBytesBeforeFirstPublication() = withStore { store ->
        val document = document(acquisition = JobConfig(retainRaw = true))

        val failure = assertThrows(ArtifactFilesException::class.java) {
            store.write(document)
        }

        assertEquals(ArtifactFilesException.RAW_REQUIRED, failure.reason)
        assertFalse(store.canonicalFile(document.artifactId).isFile)
        assertTrue(store.recoverable().isEmpty())
    }

    @Test
    fun finalizedRawBindingRejectsNoRawNewRawAndTamperedMetadata() = withStore { store ->
        val document = document(acquisition = JobConfig(retainRaw = true))
        val raw = "original raw".toByteArray()
        val stored = store.write(document, raw, "txt")

        val noRaw = assertThrows(ArtifactFilesException::class.java) {
            store.write(document)
        }
        assertEquals(ArtifactFilesException.CONFLICTING_CONTENT, noRaw.reason)

        val newRaw = assertThrows(ArtifactFilesException::class.java) {
            store.write(document, "different raw".toByteArray(), "txt")
        }
        assertEquals(ArtifactFilesException.CONFLICTING_CONTENT, newRaw.reason)
        assertArrayEquals(raw, store.rawFile(document.artifactId)!!.readBytes())

        val marker = store.canonicalFile(document.artifactId).toPath().parent.resolve(".commit.json")
        val tamperedMetadata = """
            {
              "schemaVersion": 1,
              "artifactId": "${document.artifactId}",
              "sha256": "${stored.sha256}",
              "bytes": ${stored.bytes},
              "rawExtension": "txt",
              "rawSha256": "${"0".repeat(64)}",
              "rawBytes": ${raw.size}
            }
        """.trimIndent().toByteArray()
        Files.write(marker, tamperedMetadata, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)

        val metadataFailure = assertThrows(ArtifactFilesException::class.java) {
            store.read(document.artifactId)
        }
        assertEquals(ArtifactFilesException.CORRUPT_RAW, metadataFailure.reason)
    }

    @Test
    fun rawFileTamperingIsDetectedFromPersistedHashAndSize() = withStore { store ->
        val document = document(acquisition = JobConfig(retainRaw = true))
        store.write(document, "original raw".toByteArray(), "txt")
        val rawFile = store.rawFile(document.artifactId)!!

        Files.write(
            rawFile.toPath(),
            "tampered raw".toByteArray(),
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )

        val failure = assertThrows(ArtifactFilesException::class.java) {
            store.read(document.artifactId)
        }
        assertEquals(ArtifactFilesException.CORRUPT_RAW, failure.reason)
    }

    @Test
    fun suppliedRawHashMustMatchActualRawBytesWhileNoRawHashIsAllowed() = withStore { store ->
        val raw = "raw bytes".toByteArray()
        val mismatch = document(
            artifactId = "00000000-0000-0000-0000-000000000002",
            acquisition = JobConfig(retainRaw = true),
        ).copy(rawHash = "f".repeat(64))

        val failure = assertThrows(ArtifactFilesException::class.java) {
            store.write(mismatch, raw, "txt")
        }
        assertEquals(ArtifactFilesException.RAW_HASH_MISMATCH, failure.reason)
        assertFalse(store.canonicalFile(mismatch.artifactId).isFile)

        val noRaw = document().copy(rawHash = "source-provided-hash")
        assertEquals(noRaw, store.write(noRaw).let { store.read(it.artifactId) })
    }

    @Test
    fun traversalAndSymlinkArtifactDirectoriesAreRejected() = withStore { store ->
        val traversal = assertThrows(ArtifactFilesException::class.java) {
            store.canonicalFile("../outside")
        }
        assertEquals(ArtifactFilesException.INVALID_ARTIFACT_ID, traversal.reason)

        val id = document().artifactId
        val outside = Files.createTempDirectory("sourcescribe-outside-")
        val link = rootOf(store).resolve(id)
        val sentinel = outside.resolve("sentinel")
        try {
            Files.write(sentinel, "must remain".toByteArray())
            val linked = try {
                Files.createSymbolicLink(link, outside)
                true
            } catch (_: UnsupportedOperationException) {
                false
            } catch (_: java.nio.file.FileSystemException) {
                false
            }
            assumeTrue("symbolic links are required for this check", linked)

            val symlink = assertThrows(ArtifactFilesException::class.java) {
                store.canonicalFile(id)
            }
            assertEquals(ArtifactFilesException.SYMLINK_NOT_ALLOWED, symlink.reason)
            val writeFailure = assertThrows(ArtifactFilesException::class.java) {
                store.write(document())
            }
            assertEquals(ArtifactFilesException.SYMLINK_NOT_ALLOWED, writeFailure.reason)
            assertTrue(Files.isDirectory(outside))
            assertEquals("must remain", sentinel.toFile().readText())
            assertFalse(Files.exists(outside.resolve("transcript.json")))
        } finally {
            Files.deleteIfExists(link)
            outside.toFile().deleteRecursively()
        }
    }

    @Test
    fun recoverableRejectsSymlinkRootWithoutReadingTarget() = withStore { store ->
        val root = rootOf(store)
        val outside = Files.createTempDirectory("sourcescribe-outside-root-")
        val rootLink = root.resolveSibling("sourcescribe-root-link")
        val sentinel = outside.resolve("sentinel")
        try {
            Files.write(sentinel, "must remain".toByteArray())
            val linked = try {
                Files.createSymbolicLink(rootLink, outside)
                true
            } catch (_: UnsupportedOperationException) {
                false
            } catch (_: java.nio.file.FileSystemException) {
                false
            }
            assumeTrue("symbolic links are required for this check", linked)

            val failure = assertThrows(ArtifactFilesException::class.java) {
                ArtifactFiles(rootLink.toFile()).recoverable()
            }
            assertEquals(ArtifactFilesException.SYMLINK_NOT_ALLOWED, failure.reason)
            assertEquals("must remain", sentinel.toFile().readText())
        } finally {
            Files.deleteIfExists(rootLink)
            outside.toFile().deleteRecursively()
        }
    }

    @Test
    fun partialTemporaryFilesDoNotHideOrDeleteFinalizedArtifact() = withStore { store ->
        val document = document()
        val stored = store.write(document)
        val directory = store.canonicalFile(document.artifactId).toPath().parent

        Files.write(directory.resolve(".artifact-crash.tmp"), "truncated".toByteArray())

        assertEquals(listOf(stored), store.recoverable())
        assertEquals(document, store.read(document.artifactId))
    }

    @Test
    fun corruptedCanonicalIsReportedByIntegrityCheck() = withStore { store ->
        val document = document()
        store.write(document)
        Files.write(
            store.canonicalFile(document.artifactId).toPath(),
            "{\"corrupted\":true}".toByteArray(),
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )

        val failure = assertThrows(ArtifactFilesException::class.java) {
            store.read(document.artifactId)
        }
        assertEquals(ArtifactFilesException.CORRUPT_CANONICAL, failure.reason)
    }

    @Test
    fun perIdRecoveryIsolatesCorruptedSiblingAndIgnoresAbsentOrIncompleteArtifacts() = withStore { store ->
        val valid = document()
        val corrupted = document(artifactId = "00000000-0000-0000-0000-000000000002")
        val incomplete = document(artifactId = "00000000-0000-0000-0000-000000000003")
        val stored = store.write(valid)
        store.write(corrupted)
        Files.write(
            store.canonicalFile(corrupted.artifactId).toPath(),
            "{\"corrupted\":true}".toByteArray(),
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        Files.createDirectories(store.canonicalFile(incomplete.artifactId).toPath().parent)
        Files.write(store.canonicalFile(incomplete.artifactId).toPath(), "{}".toByteArray())

        assertEquals(stored, store.recoverable(valid.artifactId))
        assertNull(store.recoverable(incomplete.artifactId))
        assertNull(store.recoverable("00000000-0000-0000-0000-000000000004"))
        val failure = assertThrows(ArtifactFilesException::class.java) {
            store.recoverable(corrupted.artifactId)
        }
        assertEquals(ArtifactFilesException.CORRUPT_CANONICAL, failure.reason)
    }

    @Test
    fun sizeBoundsAndRetentionBoundaryAreEnforced() = withStore { store ->
        // Neither input below can fail its check whatever the constant says: the document is built from
        // text as long as the bound and carries the whole record around that text as well, so it exceeds
        // every value the constant could take, and the raw array is the bound plus one, which is larger
        // than the bound for the same reason one is larger than zero. What that does check is the
        // comparison itself — `>` and not `>=`, and the reason it reports. The two numbers are stated in
        // `StatedNumbersTest`, which is where a changed value fails.
        val oversizedCanonical = document(segments = listOf(Segment("x".repeat(ArtifactFiles.MAX_CANONICAL_BYTES))))
        val canonicalFailure = assertThrows(ArtifactFilesException::class.java) {
            store.write(oversizedCanonical)
        }
        assertEquals(ArtifactFilesException.CANONICAL_TOO_LARGE, canonicalFailure.reason)

        val retained = document(acquisition = JobConfig(retainRaw = true))
        val rawFailure = assertThrows(ArtifactFilesException::class.java) {
            store.write(retained, ByteArray(ArtifactFiles.MAX_RAW_BYTES + 1), "txt")
        }
        assertEquals(ArtifactFilesException.RAW_TOO_LARGE, rawFailure.reason)

        val notRetained = assertThrows(ArtifactFilesException::class.java) {
            store.write(document(), byteArrayOf(1), "txt")
        }
        assertEquals(ArtifactFilesException.RAW_NOT_ALLOWED, notRetained.reason)
        assertNull(store.rawFile(document().artifactId))
    }

    private fun document(
        artifactId: String = "00000000-0000-0000-0000-000000000001",
        acquisition: JobConfig = JobConfig(),
        provenance: Provenance = Provenance(origin = Origin.YOUTUBE),
        segments: List<Segment> = listOf(Segment("hello", 0, 1000, timeEvidence = TimeEvidence.CAPTION_CUE)),
    ): TranscriptDocument = TranscriptDocument(
        artifactId = artifactId,
        source = Source(
            id = "youtube:BaW_jenozKc",
            kind = SourceKind.YOUTUBE,
            canonicalUrl = "https://www.youtube.com/watch?v=BaW_jenozKc",
            videoId = "BaW_jenozKc",
            title = "A title",
            originalLanguage = "de",
        ),
        acquisition = acquisition,
        provenance = provenance,
        language = "de",
        segments = segments,
        createdAt = 1,
    )

    private fun withStore(block: (ArtifactFiles) -> Unit) {
        val root = Files.createTempDirectory("sourcescribe-artifacts-")
        try {
            block(ArtifactFiles(root.toFile()))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun rootOf(store: ArtifactFiles): Path = store.canonicalFile(document().artifactId).toPath().parent.parent
}
