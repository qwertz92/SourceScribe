package app.sourcescribe.core

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderHttpTest {
    private lateinit var server: MockWebServer
    private lateinit var http: ProviderHttp

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        http = ProviderHttp(
            OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/fixture")).build())
            }.build(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun successfulResponseIsSpooledBeforeReturning() {
        val response = "saved response".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(response.decodeToString()))
        val saved = mutableListOf<ByteArray>()

        val result = runBlocking {
            http.perform(
                request(),
                mayCharge = true,
                spool = ResponseSpool { saved += it.copyOf() },
            )
        }

        assertArrayEquals(response, result)
        assertEquals(1, saved.size)
        assertArrayEquals(response, saved.single())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun paidSpoolFailureIsUncertainAndDoesNotRepeatRequest() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("response"))

        val error = assertThrows(ProviderError::class.java) {
            runBlocking {
                http.perform(
                    request(),
                    mayCharge = true,
                    spool = ResponseSpool { throw IOException("storage unavailable") },
                )
            }
        }

        assertEquals(ProviderErrorCode.SUBMISSION_UNCERTAIN, error.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun nonChargeableSpoolFailureStaysTypedAsStorageError() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("response"))

        val error = assertThrows(ProviderError::class.java) {
            runBlocking {
                http.perform(
                    request(),
                    mayCharge = false,
                    spool = ResponseSpool { throw IOException("storage unavailable") },
                )
            }
        }

        assertEquals(ProviderErrorCode.RESPONSE_STORAGE, error.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun oversizedSuccessfulResponseIsUncertainOnlyWhenChargeable() {
        val oversized = "x".repeat(16 * 1024 * 1024 + 1)
        server.enqueue(MockResponse().setResponseCode(200).setBody(oversized))
        server.enqueue(MockResponse().setResponseCode(200).setBody(oversized))

        val paidError = assertThrows(ProviderError::class.java) {
            runBlocking { http.perform(request(), mayCharge = true) }
        }
        val nonChargeableError = assertThrows(ProviderError::class.java) {
            runBlocking { http.perform(request(), mayCharge = false) }
        }

        assertEquals(ProviderErrorCode.SUBMISSION_UNCERTAIN, paidError.code)
        assertEquals(ProviderErrorCode.INVALID_RESPONSE, nonChargeableError.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun disconnectMapsToUncertaintyForPaidAndNetworkForNonChargeableCall() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val paidError = assertThrows(ProviderError::class.java) {
            runBlocking { http.perform(request(), mayCharge = true) }
        }
        val nonChargeableError = assertThrows(ProviderError::class.java) {
            runBlocking { http.perform(request(), mayCharge = false) }
        }

        assertEquals(ProviderErrorCode.SUBMISSION_UNCERTAIN, paidError.code)
        assertEquals(ProviderErrorCode.NETWORK, nonChargeableError.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun truncatedSuccessfulResponseIsUncertainWhenChargeable() {
        val body = "truncated response"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .setHeader("Content-Length", (body.length + 1).toString())
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END),
        )

        val error = assertThrows(ProviderError::class.java) {
            runBlocking { http.perform(request(), mayCharge = true) }
        }

        assertEquals(ProviderErrorCode.SUBMISSION_UNCERTAIN, error.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun cancellationPropagatesAndDoesNotRepeatHttpCall() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBodyDelay(5, TimeUnit.SECONDS)
                .setBody("delayed response"),
        )
        val requestJob = launch {
            http.perform(request(), mayCharge = true)
        }

        // Waiting on the blocking server must not prevent the child coroutine from starting.
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) })
        requestJob.cancel()
        requestJob.join()

        assertTrue(requestJob.isCancelled)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun cancellationFromSpoolIsPropagatedWithoutRetry() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("response"))

        val error = assertThrows(CancellationException::class.java) {
            runBlocking {
                http.perform(
                    request(),
                    mayCharge = true,
                    spool = ResponseSpool { throw CancellationException("cancelled") },
                )
            }
        }

        assertEquals("cancelled", error.message)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun nonAllowlistedHostIsRejectedBeforeNetwork() {
        val error = assertThrows(ProviderError::class.java) {
            runBlocking {
                http.perform(
                    Request.Builder().url("https://example.test/fixture").build(),
                    mayCharge = false,
                )
            }
        }

        assertEquals(ProviderErrorCode.INVALID_INPUT, error.code)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun httpRejectionsExposeOnlyTypedDetailsFromTheOriginalRequest() {
        val privateValues = listOf(
            "api_key_TEST_MARKER",
            "PRIVATE_TRANSCRIPT_MARKER",
            "https://private.example/audio?token=TEST_MARKER",
            "/private/source/audio.wav",
        )
        val cases = listOf(
            RejectionCase(413, "/v2/transcript", "POST", ProviderOperation.SUBMIT, ProviderRejectionReason.FILE_TOO_LARGE),
            RejectionCase(415, "/v2/upload", "POST", ProviderOperation.UPLOAD, ProviderRejectionReason.UNSUPPORTED_MEDIA),
            RejectionCase(400, "/v1/audio/transcriptions", "POST", ProviderOperation.SUBMIT, ProviderRejectionReason.UNKNOWN),
            RejectionCase(422, "/v2/transcript/00000000-0000-0000-0000-000000000000", "GET", ProviderOperation.RETRIEVE, ProviderRejectionReason.UNKNOWN),
            RejectionCase(400, "/v2/transcript/00000000-0000-0000-0000-000000000000", "DELETE", ProviderOperation.DELETE, ProviderRejectionReason.UNKNOWN),
        )

        for (case in cases) {
            val requestsBefore = server.requestCount
            val maliciousBody = """{"error":{"message":"User payload mentions invalid model, unsupported media, and language; ${privateValues.joinToString(" ")}","param":"input"}}"""
            server.enqueue(MockResponse().setResponseCode(case.status).setBody(maliciousBody))

            val error = assertThrows(ProviderError::class.java) {
                runBlocking { http.perform(operationRequest(case.path, case.method), mayCharge = true) }
            }

            assertEquals(case.status, error.httpStatus)
            assertEquals(ProviderErrorCode.INVALID_INPUT, error.code)
            assertEquals(
                ProviderFailure(case.status, case.operation, case.reason),
                error.failure,
            )
            assertEquals("INVALID_INPUT", error.message)
            val visible = "${error.message} $error ${error.failure}"
            privateValues.forEach { assertFalse("diagnostic leaked $it", visible.contains(it)) }
            assertEquals(requestsBefore + 1, server.requestCount)
            // The interceptor rewrites this URL to /fixture; diagnostics must use the caller's request.
            assertEquals("/fixture", server.takeRequest().path)
        }
    }

    @Test
    fun oversizedHttpErrorBodyIsIgnoredWithoutLeakingItsContents() {
        val privateMarker = "PRIVATE_ERROR_BODY_MARKER"
        val body = "{\"error\":{\"message\":\"Unsupported model " + "x".repeat(20 * 1024) + privateMarker + "\"}}"
        server.enqueue(MockResponse().setResponseCode(422).setBody(body))

        val error = assertThrows(ProviderError::class.java) {
            runBlocking { http.perform(operationRequest("/v1/audio/transcriptions", "POST"), mayCharge = true) }
        }

        assertEquals(ProviderRejectionReason.UNKNOWN, error.failure?.reason)
        assertFalse("diagnostic leaked oversized response body", "${error.message} $error ${error.failure}".contains(privateMarker))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun transportFailureIncludesOriginalOperationWithoutRetry() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val error = assertThrows(ProviderError::class.java) {
            runBlocking { http.perform(operationRequest("/v1/audio/transcriptions", "POST"), mayCharge = true) }
        }

        assertEquals(ProviderErrorCode.SUBMISSION_UNCERTAIN, error.code)
        assertEquals(ProviderFailure(operation = ProviderOperation.SUBMIT), error.failure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun classifiesOnlyExplicitProviderFieldRejections() {
        val cases = listOf(
            """{"error":{"message":"Invalid model requested","param":"model"}}""" to ProviderRejectionReason.INVALID_MODEL,
            """{"error":{"message":"The language_code is not supported by this model","param":"language_code"}}""" to ProviderRejectionReason.INVALID_LANGUAGE,
            """{"error":{"message":"Unsupported response_format value","param":"response_format"}}""" to ProviderRejectionReason.INVALID_OPTION,
            """{"error":{"message":"Audio file is invalid","param":"file"}}""" to ProviderRejectionReason.INVALID_AUDIO,
        )

        for ((body, expectedReason) in cases) {
            server.enqueue(MockResponse().setResponseCode(422).setBody(body))
            val error = assertThrows(ProviderError::class.java) {
                runBlocking { http.perform(operationRequest("/v1/audio/transcriptions", "POST"), mayCharge = true) }
            }
            assertEquals(expectedReason, error.failure?.reason)
        }
    }

    @Test
    fun anUploadReportsHowMuchOfItsBodyHasBeenWritten() {
        // What the job card counts up while a chunk goes to the provider. The report has to come from the
        // bytes that reach the socket: a screen that showed the request as sent the moment it was handed to
        // the client would stand at a hundred percent for the whole upload of a ten-minute chunk.
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val audio = ByteArray(512 * 1024) { (it and 0xff).toByte() }
        val reports = java.util.Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())

        runBlocking {
            withContext(UploadProgress { written, total -> reports += written to total }) {
                http.perform(upload(audio), mayCharge = true)
            }
        }

        assertTrue("no progress reported", reports.isNotEmpty())
        // Every report names the same total, counts up, and the last one is the whole body.
        assertTrue(reports.all { it.second == audio.size.toLong() })
        assertEquals(reports.map { it.first }.sorted(), reports.map { it.first })
        assertEquals(audio.size.toLong(), reports.last().first)
        // And the request itself is untouched: the server received the body byte for byte.
        assertArrayEquals(audio, server.takeRequest().body.readByteArray())
    }

    @Test
    fun anUploadNobodyIsWatchingIsSentUnchanged() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val audio = ByteArray(4096) { (it and 0xff).toByte() }

        runBlocking { http.perform(upload(audio), mayCharge = true) }

        assertArrayEquals(audio, server.takeRequest().body.readByteArray())
    }

    @Test
    fun assemblyAiJsonSubmitDoesNotResetUploadProgress() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("upload-url"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val audio = ByteArray(4096) { (it and 0xff).toByte() }
        val json = "{\"audio_url\":\"https://upload.example/audio\"}"
        val reports = java.util.Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
        val uploadRequest = Request.Builder()
            .url("https://api.assemblyai.com/v2/upload")
            .post(audio.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        val submitRequest = Request.Builder()
            .url("https://api.assemblyai.com/v2/transcript")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        runBlocking {
            withContext(UploadProgress { written, total -> reports += written to total }) {
                http.perform(uploadRequest, mayCharge = false)
                http.perform(submitRequest, mayCharge = true)
            }
        }

        assertTrue(reports.isNotEmpty())
        assertTrue(reports.all { it.second == audio.size.toLong() })
        assertEquals(audio.size.toLong(), reports.last().first)
        assertEquals(2, server.requestCount)
        assertArrayEquals(audio, server.takeRequest().body.readByteArray())
        assertEquals(json, server.takeRequest().body.readUtf8())
    }

    @Test
    fun multipartUploadProgressIncludesTheMultipartEnvelope() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val audio = ByteArray(4096) { (it and 0xff).toByte() }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.mp3", audio.toRequestBody("audio/mpeg".toMediaType()))
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .build()
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .post(body)
            .build()
        val total = body.contentLength()
        val reports = java.util.Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())

        runBlocking {
            withContext(UploadProgress { written, requestTotal -> reports += written to requestTotal }) {
                http.perform(request, mayCharge = true)
            }
        }

        assertTrue(reports.isNotEmpty())
        assertTrue(reports.all { it.second == total })
        assertEquals(total, reports.last().first)
        assertEquals(total, server.takeRequest().body.size)
    }

    private fun request(): Request = Request.Builder()
        .url("https://api.groq.com/openai/v1/audio/transcriptions")
        .get()
        .build()

    private fun operationRequest(path: String, method: String): Request {
        val body = if (method == "GET" || method == "DELETE") null
        else ByteArray(0).toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("https://api.groq.com$path")
            .method(method, body)
            .build()
    }

    private data class RejectionCase(
        val status: Int,
        val path: String,
        val method: String,
        val operation: ProviderOperation,
        val reason: ProviderRejectionReason,
    )

    private fun upload(body: ByteArray): Request = Request.Builder()
        .url("https://api.groq.com/openai/v1/audio/transcriptions")
        .post(body.toRequestBody("audio/mpeg".toMediaType()))
        .build()
}
