package app.sourcescribe.core.providers

import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderAdapter
import app.sourcescribe.core.ProviderError
import app.sourcescribe.core.ProviderErrorCode
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.ResponseSpool
import app.sourcescribe.core.TranscriptionRequest
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderFailureMatrixTest {
    @Test
    fun everyAdapterMapsFailureMatrixOverTlsWithoutRetryOrBodyLeak() {
        val fixture = TlsFixture()
        val audio = audioFile()
        try {
            val cases = adapterCases(fixture.http, audio)
            for (adapterCase in cases) {
                for (failure in Failure.entries) {
                    val context = "${adapterCase.adapter.provider}/${failure.name}"
                    val requestCountBefore = fixture.server.requestCount
                    if (adapterCase.hasUploadPrelude) {
                        fixture.server.enqueue(uploadResponse())
                    }
                    fixture.server.enqueue(failure.response())

                    val error = assertThrows(ProviderError::class.java) {
                        runBlocking {
                            adapterCase.adapter.submit(
                                adapterCase.request,
                                "matrix-test-key",
                                ResponseSpool {},
                            )
                        }
                    }

                    assertEquals(context, failure.expectedCode, error.code)
                    assertEquals(context, failure.httpStatus, error.httpStatus)
                    assertEquals(failure.expectedCode.name, error.message)
                    assertFalse(error.message.orEmpty().contains("provider secret", ignoreCase = true))
                    assertEquals(
                        requestCountBefore + if (adapterCase.hasUploadPrelude) 2 else 1,
                        fixture.server.requestCount,
                    )

                    if (adapterCase.hasUploadPrelude) {
                        assertEquals("/v2/upload", takeRequest(fixture.server).path)
                    }
                    assertEquals(adapterCase.path, takeRequest(fixture.server).path)
                }
            }
            assertNull(takeRequestOrNull(fixture.server))
        } finally {
            audio.delete()
            fixture.close()
        }
    }

    @Test
    fun unsupportedOptionsAreRejectedBeforeAnyTlsRequest() {
        val fixture = TlsFixture()
        val audio = audioFile()
        try {
            val cases = listOf(
                AssemblyAiAdapter(fixture.http) to request(
                    Provider.ASSEMBLYAI,
                    "unsupported-model",
                    audio,
                ),
                OpenAiAdapter(fixture.http) to request(
                    Provider.OPENAI,
                    OpenAiAdapter.MODEL_GPT_TRANSCRIBE,
                    audio,
                ),
                GroqAdapter(fixture.http) to request(
                    Provider.GROQ,
                    GroqAdapter.MODEL_TURBO,
                    audio,
                    diarization = true,
                ),
            )

            for ((adapter, request) in cases) {
                val error = assertThrows(ProviderError::class.java) {
                    runBlocking {
                        adapter.submit(
                            request,
                            "matrix-test-key",
                            ResponseSpool {},
                        )
                    }
                }
                assertEquals(ProviderErrorCode.UNSUPPORTED_OPTION, error.code)
                assertEquals(0, fixture.server.requestCount)
            }
        } finally {
            audio.delete()
            fixture.close()
        }
    }

    @Test
    fun everyAdapterMapsPaidHttpsReadTimeoutAsUncertainWithoutRetry() {
        val fixture = TlsFixture(readTimeoutMillis = 150)
        val audio = audioFile()
        try {
            for (adapterCase in adapterCases(fixture.http, audio)) {
                val requestCountBefore = fixture.server.requestCount
                if (adapterCase.hasUploadPrelude) fixture.server.enqueue(uploadResponse())
                fixture.server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("{}")
                        .setBodyDelay(800, TimeUnit.MILLISECONDS),
                )

                val error = assertThrows(ProviderError::class.java) {
                    runBlocking {
                        adapterCase.adapter.submit(adapterCase.request, "matrix-test-key", ResponseSpool {})
                    }
                }

                assertEquals(
                    adapterCase.adapter.provider.name,
                    ProviderErrorCode.SUBMISSION_UNCERTAIN,
                    error.code,
                )
                assertNull(error.httpStatus)
                assertEquals(
                    requestCountBefore + if (adapterCase.hasUploadPrelude) 2 else 1,
                    fixture.server.requestCount,
                )
                if (adapterCase.hasUploadPrelude) assertEquals("/v2/upload", takeRequest(fixture.server).path)
                assertEquals(adapterCase.path, takeRequest(fixture.server).path)
                assertNull(takeRequestOrNull(fixture.server))
            }
        } finally {
            audio.delete()
            fixture.close()
        }
    }

    @Test
    fun everyAdapterRejectsHttpsRedirectWithoutLeakingAuthorizationOrRetrying() {
        val fixture = TlsFixture()
        val redirectTarget = fixture.newServer()
        val audio = audioFile()
        try {
            repeat(3) { redirectTarget.enqueue(MockResponse().setResponseCode(418)) }
            for (adapterCase in adapterCases(fixture.http, audio)) {
                val requestCountBefore = fixture.server.requestCount
                if (adapterCase.hasUploadPrelude) fixture.server.enqueue(uploadResponse())
                fixture.server.enqueue(
                    MockResponse()
                        .setResponseCode(307)
                        .setHeader("Location", redirectTarget.url("/authorization-canary")),
                )

                val error = assertThrows(ProviderError::class.java) {
                    runBlocking {
                        adapterCase.adapter.submit(adapterCase.request, "matrix-test-key", ResponseSpool {})
                    }
                }

                assertEquals(
                    adapterCase.adapter.provider.name,
                    ProviderErrorCode.INVALID_INPUT,
                    error.code,
                )
                assertEquals(307, error.httpStatus)
                assertEquals(
                    requestCountBefore + if (adapterCase.hasUploadPrelude) 2 else 1,
                    fixture.server.requestCount,
                )
                if (adapterCase.hasUploadPrelude) assertEquals("/v2/upload", takeRequest(fixture.server).path)
                val paidRequest = takeRequest(fixture.server)
                assertEquals(adapterCase.path, paidRequest.path)
                assertEquals(adapterCase.authorization, paidRequest.getHeader("Authorization"))
                assertNull(takeRequestOrNull(fixture.server))
            }
            assertEquals(0, redirectTarget.requestCount)
            assertNull(takeRequestOrNull(redirectTarget))
        } finally {
            audio.delete()
            redirectTarget.shutdown()
            fixture.close()
        }
    }

    private fun adapterCases(http: ProviderHttp, audio: File): List<AdapterCase> = listOf(
        AdapterCase(
            AssemblyAiAdapter(http),
            request(Provider.ASSEMBLYAI, AssemblyAiAdapter.MODEL_U2, audio),
            "/v2/transcript",
            "matrix-test-key",
            hasUploadPrelude = true,
        ),
        AdapterCase(
            OpenAiAdapter(http),
            request(Provider.OPENAI, OpenAiAdapter.MODEL_WHISPER_1, audio),
            "/v1/audio/transcriptions",
            "Bearer matrix-test-key",
            hasUploadPrelude = false,
        ),
        AdapterCase(
            GroqAdapter(http),
            request(Provider.GROQ, GroqAdapter.MODEL_TURBO, audio),
            "/openai/v1/audio/transcriptions",
            "Bearer matrix-test-key",
            hasUploadPrelude = false,
        ),
    )

    private fun request(
        provider: Provider,
        model: String,
        audio: File,
        diarization: Boolean = false,
    ): TranscriptionRequest =
        TranscriptionRequest(
            audio = audio,
            mimeType = "audio/wav",
            config = JobConfig(
                mode = AcquisitionMode.STT_ONLY,
                provider = provider,
                model = model,
                diarization = diarization,
                uploadApproved = true,
            ),
            durationMs = 2_000,
        )

    private fun audioFile(): File = Files.createTempFile("sourcescribe-provider-matrix-", ".wav").toFile().apply {
        writeBytes(byteArrayOf(1, 2, 3, 4))
    }

    private fun uploadResponse(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody(fixture("/providers/assembly/upload.json"))

    private fun fixture(path: String): String =
        requireNotNull(javaClass.getResource(path)).readText(StandardCharsets.UTF_8)

    private fun takeRequest(server: MockWebServer) =
        requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)) { "fixture request was not received" }

    private fun takeRequestOrNull(server: MockWebServer) = server.takeRequest(1, TimeUnit.SECONDS)

    private enum class Failure(
        val expectedCode: ProviderErrorCode,
        val httpStatus: Int?,
        private val status: Int? = null,
        private val disconnect: Boolean = false,
        private val body: String = "provider secret detail",
    ) {
        AUTHENTICATION_401(ProviderErrorCode.AUTHENTICATION, 401, status = 401),
        ACCESS_DENIED_403(ProviderErrorCode.ACCESS_DENIED, 403, status = 403),
        TIMEOUT_408(ProviderErrorCode.SUBMISSION_UNCERTAIN, 408, status = 408),
        RATE_LIMIT_429(ProviderErrorCode.RATE_LIMIT, 429, status = 429),
        SERVER_500(ProviderErrorCode.SUBMISSION_UNCERTAIN, 500, status = 500),
        SERVER_503(ProviderErrorCode.SUBMISSION_UNCERTAIN, 503, status = 503),
        DISCONNECT(ProviderErrorCode.SUBMISSION_UNCERTAIN, null, disconnect = true),
        INVALID_JSON(ProviderErrorCode.INVALID_RESPONSE, null, body = "not-json"),
        MISSING_TEXT(
            ProviderErrorCode.INVALID_RESPONSE,
            null,
            body = "{\"id\":\"0072a82b-aa22-4962-add2-6121c36c17c6\",\"status\":\"completed\"}",
        ),
        ;

        fun response(): MockResponse = when {
            disconnect -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            status != null -> MockResponse()
                .setResponseCode(status)
                .setHeader("Retry-After", "17")
                .setBody(body)
            else -> MockResponse().setResponseCode(200).setBody(body)
        }
    }

    private data class AdapterCase(
        val adapter: ProviderAdapter,
        val request: TranscriptionRequest,
        val path: String,
        val authorization: String,
        val hasUploadPrelude: Boolean,
    )

    private class TlsFixture(private val readTimeoutMillis: Int? = null) {
        private val tls = TlsMaterial.create()
        val server = MockWebServer()
        val http: ProviderHttp

        init {
            server.useHttps(tls.sslContext.socketFactory, false)
            server.start()
            val fixtureUrl = server.url("/fixture")
            val client = OkHttpClient.Builder()
                .sslSocketFactory(tls.sslContext.socketFactory, tls.trustManager)
                .hostnameVerifier(HostnameVerifier { host, _ -> host == fixtureUrl.host })
                .addInterceptor { chain ->
                    val original = chain.request()
                    val rewritten = original.url.newBuilder()
                        .scheme(fixtureUrl.scheme)
                        .host(fixtureUrl.host)
                        .port(fixtureUrl.port)
                        .build()
                    val routed = original.newBuilder().url(rewritten).build()
                    val timedChain = readTimeoutMillis
                        ?.let { chain.withReadTimeout(it, TimeUnit.MILLISECONDS) }
                        ?: chain
                    timedChain.proceed(routed)
                }
                .build()
            http = ProviderHttp(client)
        }

        fun newServer(): MockWebServer = MockWebServer().apply {
            useHttps(tls.sslContext.socketFactory, false)
            start()
        }

        fun close() {
            server.shutdown()
            tls.close()
        }
    }

    private class TlsMaterial private constructor(
        val sslContext: SSLContext,
        val trustManager: X509TrustManager,
        private val directory: File,
        private val keyStoreFile: File,
    ) {
        fun close() {
            keyStoreFile.delete()
            directory.delete()
        }

        companion object {
            private const val PASSWORD = "sourcescribe-test"

            fun create(): TlsMaterial {
                val directory = Files.createTempDirectory("sourcescribe-provider-tls-").toFile()
                val keyStoreFile = File(directory, "fixture.p12")
                val keytool = File(
                    System.getProperty("java.home"),
                    "bin${File.separator}keytool${if (File.separatorChar == '\\') ".exe" else ""}",
                )
                val process = ProcessBuilder(
                    keytool.absolutePath,
                    "-genkeypair",
                    "-alias", "fixture",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-validity", "1",
                    "-dname", "CN=localhost",
                    "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                    "-keystore", keyStoreFile.absolutePath,
                    "-storetype", "PKCS12",
                    "-storepass", PASSWORD,
                    "-keypass", PASSWORD,
                    "-noprompt",
                ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    throw AssertionError("keytool could not create the TLS fixture within 5 seconds")
                }
                assertEquals(0, process.exitValue())

                val keyStore = KeyStore.getInstance("PKCS12").apply {
                    keyStoreFile.inputStream().use { load(it, PASSWORD.toCharArray()) }
                }
                val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                    init(keyStore, PASSWORD.toCharArray())
                }.keyManagers
                val trustStore = KeyStore.getInstance("PKCS12").apply {
                    load(null, null)
                    setCertificateEntry("fixture", keyStore.getCertificate("fixture"))
                }
                val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                    init(trustStore)
                }.trustManagers
                val trustManager = trustManagers.filterIsInstance<X509TrustManager>().single()
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(keyManagers, trustManagers, SecureRandom())
                }
                return TlsMaterial(sslContext, trustManager, directory, keyStoreFile)
            }
        }
    }
}
