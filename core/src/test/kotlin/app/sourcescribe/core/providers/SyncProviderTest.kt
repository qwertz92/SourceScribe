package app.sourcescribe.core.providers

import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderAdapter
import app.sourcescribe.core.ProviderError
import app.sourcescribe.core.ProviderErrorCode
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.Region
import app.sourcescribe.core.ResponseSpool
import app.sourcescribe.core.Segment
import app.sourcescribe.core.SubmissionResult
import app.sourcescribe.core.TimeEvidence
import app.sourcescribe.core.TranscriptionRequest
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncProviderTest {
    @Test
    fun capabilityMatrixRejectsUnsupportedModelOptionsBeforeUpload() {
        val groq = GroqAdapter()
        assertTrue(groq.capabilities(GroqAdapter.MODEL_V3).wordTimestamps)
        assertTrue(groq.capabilities(GroqAdapter.MODEL_TURBO).segmentTimestamps)
        assertFalse(groq.capabilities(GroqAdapter.MODEL_V3).diarization)
        assertEquals(111_000L, groq.capabilities(GroqAdapter.MODEL_V3).priceMicrousdPerHour)
        assertEquals(40_000L, groq.capabilities(GroqAdapter.MODEL_TURBO).priceMicrousdPerHour)

        val openAi = OpenAiAdapter()
        val gpt = openAi.capabilities(OpenAiAdapter.MODEL_GPT_TRANSCRIBE)
        assertFalse(gpt.wordTimestamps)
        assertFalse(gpt.segmentTimestamps)
        assertTrue(gpt.contextTerms)
        assertEquals(270_000L, gpt.priceMicrousdPerHour)
        assertTrue(openAi.capabilities(OpenAiAdapter.MODEL_WHISPER_1).wordTimestamps)
        assertTrue(openAi.capabilities(OpenAiAdapter.MODEL_GPT_4O_TRANSCRIBE_DIARIZE).diarization)
        assertNull(openAi.capabilities(OpenAiAdapter.MODEL_GPT_4O_TRANSCRIBE_DIARIZE).priceMicrousdPerHour)
        assertThrows(ProviderError::class.java) { openAi.capabilities("unknown-model") }

        val server = MockWebServer()
        server.start()
        try {
            val request = request(
                Provider.OPENAI,
                OpenAiAdapter.MODEL_GPT_TRANSCRIBE,
                segmentTimestamps = true,
            )
            val error = assertThrows(ProviderError::class.java) {
                runBlocking { OpenAiAdapter(providerHttp(server)).submit(request, "test-key", ResponseSpool {}) }
            }
            assertEquals(ProviderErrorCode.UNSUPPORTED_OPTION, error.code)

            val unsupportedRequests: List<Pair<TranscriptionRequest, ProviderAdapter>> = listOf(
                request(
                    Provider.GROQ,
                    GroqAdapter.MODEL_TURBO,
                    diarization = true,
                ) to GroqAdapter(providerHttp(server)),
                request(
                    Provider.OPENAI,
                    OpenAiAdapter.MODEL_GPT_TRANSCRIBE,
                    wordTimestamps = true,
                    segmentTimestamps = false,
                ) to OpenAiAdapter(providerHttp(server)),
                request(
                    Provider.OPENAI,
                    OpenAiAdapter.MODEL_GPT_4O_TRANSCRIBE_DIARIZE,
                    wordTimestamps = true,
                ) to OpenAiAdapter(providerHttp(server)),
                request(
                    Provider.OPENAI,
                    OpenAiAdapter.MODEL_GPT_4O_TRANSCRIBE_DIARIZE,
                    contextTerms = listOf("term"),
                ) to OpenAiAdapter(providerHttp(server)),
            )
            for ((unsupportedRequest, adapter) in unsupportedRequests) {
                val unsupportedError = assertThrows(ProviderError::class.java) {
                    runBlocking { adapter.submit(unsupportedRequest, "test-key", ResponseSpool {}) }
                }
                assertEquals(ProviderErrorCode.UNSUPPORTED_OPTION, unsupportedError.code)
            }
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun groqRequestAndResponseAreParsedAndSpooled() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(fixture("groq_verbose.json")))
        server.start()
        try {
            val request = request(
                Provider.GROQ,
                GroqAdapter.MODEL_TURBO,
                language = "DE",
                wordTimestamps = true,
                segmentTimestamps = true,
                contextTerms = listOf("Kotlin", "SourceScribe"),
                chunkIndex = 2,
                chunkStartMs = 10_000,
            )
            var saved: ByteArray? = null
            val result = runBlocking {
                GroqAdapter(providerHttp(server)).submit(request, "groq-secret", ResponseSpool { saved = it })
            } as SubmissionResult.Direct
            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            val body = recorded!!.body.readUtf8()
            assertEquals("Bearer groq-secret", recorded.getHeader("Authorization"))
            assertTrue(body.contains("name=\"model\""))
            assertTrue(body.contains("whisper-large-v3-turbo"))
            assertTrue(body.contains("name=\"language\""))
            assertTrue(body.contains("\r\n\r\nde\r\n"))
            assertTrue(body.contains("name=\"prompt\""))
            assertTrue(body.contains("Kotlin, SourceScribe"))
            assertTrue(body.contains("name=\"response_format\""))
            assertTrue(body.contains("verbose_json"))
            assertTrue(body.contains("name=\"timestamp_granularities[]\""))
            assertTrue(body.contains("\r\n\r\nword\r\n"))
            assertTrue(body.contains("\r\n\r\nsegment\r\n"))
            assertEquals(fixture("groq_verbose.json"), saved!!.toString(StandardCharsets.UTF_8))
            assertEquals("en", result.transcript.language)
            assertEquals(listOf("en"), result.transcript.reportedLanguages)
            assertNull(result.transcript.reportedModel)
            assertEquals(listOf("Hello", "world"), result.transcript.segments.map(Segment::text))
            assertEquals(10_000L, result.transcript.segments.first().startMs)
            assertEquals(TimeEvidence.PROVIDER_SEGMENT, result.transcript.segments.first().timeEvidence)
            assertEquals(listOf("Hello", "world"), result.transcript.words.map(Segment::text))
            assertEquals(listOf(10_000L, 11_000L), result.transcript.words.map(Segment::startMs))
            assertEquals(listOf(10_500L, 11_500L), result.transcript.words.map(Segment::endMs))
            assertTrue(result.transcript.words.all { it.timeEvidence == TimeEvidence.PROVIDER_WORD })
            assertTrue(result.transcript.technicallyComplete)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun openAiModelsUseSeparateRequestSchemasAndParseTheirShapes() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(fixture("openai_gpt.json")))
        server.enqueue(MockResponse().setResponseCode(200).setBody(fixture("openai_whisper_verbose.json")))
        server.enqueue(MockResponse().setResponseCode(200).setBody(fixture("openai_diarized.json")))
        server.start()
        try {
            val gptRequest = request(
                Provider.OPENAI,
                OpenAiAdapter.MODEL_GPT_TRANSCRIBE,
                language = "de",
                segmentTimestamps = false,
                contextTerms = listOf("FinOps", "Kotlin"),
            )
            val gpt = runBlocking {
                OpenAiAdapter(providerHttp(server)).submit(gptRequest, "openai-secret", ResponseSpool {})
            } as SubmissionResult.Direct
            val gptRecorded = server.takeRequest(5, TimeUnit.SECONDS)!!
            val gptBody = gptRecorded.body.readUtf8()
            assertEquals("Bearer openai-secret", gptRecorded.getHeader("Authorization"))
            assertTrue(gptBody.contains("gpt-transcribe"))
            assertTrue(gptBody.contains("name=\"keywords[]\""))
            assertTrue(gptBody.contains("FinOps"))
            assertTrue(gptBody.contains("Kotlin"))
            assertTrue(gptBody.contains("response_format"))
            assertTrue(gptBody.contains("json"))
            assertFalse(gptBody.contains("timestamp_granularities[]"))
            assertFalse(gptBody.contains("name=\"languages\""))
            assertEquals(listOf("A direct transcript"), gpt.transcript.segments.map(Segment::text))
            assertEquals("de", gpt.transcript.language)
            assertEquals(listOf("de"), gpt.transcript.reportedLanguages)
            assertTrue(gpt.transcript.technicallyComplete)

            val whisperRequest = request(
                Provider.OPENAI,
                OpenAiAdapter.MODEL_WHISPER_1,
                wordTimestamps = true,
                segmentTimestamps = true,
                contextTerms = listOf("Whisper"),
            )
            val whisper = runBlocking {
                OpenAiAdapter(providerHttp(server)).submit(whisperRequest, "openai-secret", ResponseSpool {})
            } as SubmissionResult.Direct
            val whisperBody = server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()
            assertTrue(whisperBody.contains("whisper-1"))
            assertTrue(whisperBody.contains("name=\"prompt\""))
            assertTrue(whisperBody.contains("verbose_json"))
            assertTrue(whisperBody.contains("\r\n\r\nword\r\n"))
            assertTrue(whisperBody.contains("\r\n\r\nsegment\r\n"))
            assertFalse(whisperBody.contains("name=\"languages\""))
            assertEquals(TimeEvidence.PROVIDER_SEGMENT, whisper.transcript.segments.single().timeEvidence)
            assertEquals(250L, whisper.transcript.segments.single().startMs)
            assertEquals(listOf("Hello", "world"), whisper.transcript.words.map(Segment::text))
            assertEquals(listOf(250L, 800L), whisper.transcript.words.map(Segment::startMs))
            assertEquals(listOf(750L, 1_250L), whisper.transcript.words.map(Segment::endMs))
            assertTrue(whisper.transcript.words.all { it.timeEvidence == TimeEvidence.PROVIDER_WORD })

            val diarizedRequest = request(
                Provider.OPENAI,
                OpenAiAdapter.MODEL_GPT_4O_TRANSCRIBE_DIARIZE,
                diarization = true,
                durationMs = 31_000,
            )
            val diarized = runBlocking {
                OpenAiAdapter(providerHttp(server)).submit(diarizedRequest, "openai-secret", ResponseSpool {})
            } as SubmissionResult.Direct
            val diarizedBody = server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()
            assertTrue(diarizedBody.contains("gpt-4o-transcribe-diarize"))
            assertTrue(diarizedBody.contains("diarized_json"))
            assertTrue(diarizedBody.contains("chunking_strategy"))
            assertTrue(diarizedBody.contains("auto"))
            assertFalse(diarizedBody.contains("timestamp_granularities[]"))
            assertFalse(diarizedBody.contains("prompt"))
            assertEquals("chunk-0:A", diarized.transcript.segments.first().speaker)
            assertEquals(TimeEvidence.PROVIDER_SEGMENT, diarized.transcript.segments.first().timeEvidence)
            assertTrue(diarized.transcript.technicallyComplete)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun malformedResponsesAndMissingTimestampExtentsAreTypedAndVisible() {
        val request = request(
            Provider.GROQ,
            GroqAdapter.MODEL_TURBO,
            wordTimestamps = true,
            segmentTimestamps = true,
        )
        val adapter = GroqAdapter()
        val incomplete = adapter.parseSavedResponse(
            fixture("groq_missing_word_time.json").toByteArray(StandardCharsets.UTF_8), request,
        ) as SubmissionResult.Direct
        assertFalse(incomplete.transcript.technicallyComplete)
        assertTrue(incomplete.transcript.warnings.any { it.contains("WORD") })
        assertEquals(TimeEvidence.PROVIDER_SEGMENT, incomplete.transcript.segments.single().timeEvidence)
        assertEquals(listOf("Hello", "world"), incomplete.transcript.words.map(Segment::text))
        assertEquals(listOf(null, 1_000L), incomplete.transcript.words.map(Segment::startMs))
        assertEquals(listOf(null, 1_500L), incomplete.transcript.words.map(Segment::endMs))
        assertEquals(TimeEvidence.UNKNOWN, incomplete.transcript.words.first().timeEvidence)
        assertEquals(TimeEvidence.PROVIDER_WORD, incomplete.transcript.words.last().timeEvidence)

        for (raw in listOf("not-json", "{}", "{\"text\":\" \"}")) {
            val error = assertThrows(ProviderError::class.java) {
                adapter.parseSavedResponse(raw.toByteArray(StandardCharsets.UTF_8), request)
            }
            assertEquals(ProviderErrorCode.INVALID_RESPONSE, error.code)
        }

        val diarizedRequest = request(
            Provider.OPENAI,
            OpenAiAdapter.MODEL_GPT_4O_TRANSCRIBE_DIARIZE,
            diarization = true,
        )
        val diarized = OpenAiAdapter().parseSavedResponse(
            "{\"text\":\"hello\",\"segments\":[{\"start\":0,\"end\":1,\"text\":\"hello\"}]}"
                .toByteArray(StandardCharsets.UTF_8),
            diarizedRequest,
        ) as SubmissionResult.Direct
        assertFalse(diarized.transcript.technicallyComplete)
        assertNull(diarized.transcript.segments.single().speaker)
        assertTrue(diarized.transcript.warnings.any { it.contains("SPEAKER") })
    }

    @Test
    fun reportedLanguageArrayIsRetainedAndSingularLanguageBecomesUnknown() {
        val result = OpenAiAdapter().parseSavedResponse(
            "{\"text\":\"hello\",\"language\":\"en\",\"languages\":[{\"code\":\"de\"},{\"code\":\"en\"},\"invalid\"]}"
                .toByteArray(StandardCharsets.UTF_8),
            request(
                Provider.OPENAI,
                OpenAiAdapter.MODEL_GPT_TRANSCRIBE,
                segmentTimestamps = false,
            ),
        ) as SubmissionResult.Direct

        assertNull(result.transcript.language)
        assertEquals(listOf("en", "de"), result.transcript.reportedLanguages)
        assertTrue(result.transcript.warnings.contains("MULTIPLE_LANGUAGES"))
    }

    @Test
    fun overlongReportedModelIsDiscardedWithExplicitUnknownProvenanceWarning() {
        val result = OpenAiAdapter().parseSavedResponse(
            "{\"text\":\"hello\",\"model\":\"${"x".repeat(129)}\"}"
                .toByteArray(StandardCharsets.UTF_8),
            request(
                Provider.OPENAI,
                OpenAiAdapter.MODEL_GPT_TRANSCRIBE,
                segmentTimestamps = false,
            ),
        ) as SubmissionResult.Direct

        assertNull(result.transcript.reportedModel)
        assertTrue(result.transcript.warnings.contains("REPORTED_MODEL_TOO_LONG"))
    }

    @Test
    fun invalidTimestampExtentAndOrderPreserveTextButMarkResultIncomplete() {
        val result = GroqAdapter().parseSavedResponse(
            """
                {
                  "text":"one two",
                  "segments":[
                    {"start":0.6,"end":1.5,"text":"one"},
                    {"start":0.5,"end":1.0,"text":"two"}
                  ],
                  "words":[
                    {"word":"one","start":0.5,"end":0.7},
                    {"word":"two","start":0.4,"end":0.6}
                  ]
                }
            """.trimIndent().toByteArray(StandardCharsets.UTF_8),
            request(
                Provider.GROQ,
                GroqAdapter.MODEL_TURBO,
                wordTimestamps = true,
                segmentTimestamps = true,
                durationMs = 1_000,
            ),
        ) as SubmissionResult.Direct

        assertFalse(result.transcript.technicallyComplete)
        assertEquals(listOf("one", "two"), result.transcript.segments.map(Segment::text))
        assertEquals(listOf(null, null), result.transcript.segments.map(Segment::startMs))
        assertEquals(listOf(500L, null), result.transcript.words.map(Segment::startMs))
        assertTrue(result.transcript.warnings.any { it.startsWith("OUT_OF_RANGE_SEGMENT") })
        assertTrue(result.transcript.warnings.any { it.startsWith("NON_MONOTONIC_SEGMENT") })
        assertTrue(result.transcript.warnings.any { it.startsWith("NON_MONOTONIC_WORD") })
    }

    @Test
    fun whisperAndGroqPromptByteLimitRejectsUnicodeBeforeUpload() {
        val server = MockWebServer()
        server.start()
        try {
            val oversized = "ä".repeat(113)
            val groqError = assertThrows(ProviderError::class.java) {
                runBlocking {
                    GroqAdapter(providerHttp(server)).submit(
                        request(
                            Provider.GROQ,
                            GroqAdapter.MODEL_TURBO,
                            contextTerms = listOf(oversized),
                        ),
                        "key",
                        ResponseSpool {},
                    )
                }
            }
            assertEquals(ProviderErrorCode.INVALID_INPUT, groqError.code)

            val whisperError = assertThrows(ProviderError::class.java) {
                runBlocking {
                    OpenAiAdapter(providerHttp(server)).submit(
                        request(
                            Provider.OPENAI,
                            OpenAiAdapter.MODEL_WHISPER_1,
                            contextTerms = listOf(oversized),
                        ),
                        "key",
                        ResponseSpool {},
                    )
                }
            }
            assertEquals(ProviderErrorCode.INVALID_INPUT, whisperError.code)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun groqPromptAtConservativeUtf8BoundaryIsSentUnchanged() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(fixture("groq_verbose.json")))
        server.start()
        try {
            val term = "ä".repeat(112)
            runBlocking {
                GroqAdapter(providerHttp(server)).submit(
                    request(
                        Provider.GROQ,
                        GroqAdapter.MODEL_TURBO,
                        contextTerms = listOf(term),
                    ),
                    "key",
                    ResponseSpool {},
                )
            }
            val body = server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()
            assertTrue(body.contains(term))
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun httpStatusesMapToProviderErrorsAndSubmissionUncertainty() {
        for ((status, expected) in listOf(
            401 to ProviderErrorCode.AUTHENTICATION,
            403 to ProviderErrorCode.ACCESS_DENIED,
            429 to ProviderErrorCode.RATE_LIMIT,
            408 to ProviderErrorCode.SUBMISSION_UNCERTAIN,
            500 to ProviderErrorCode.SUBMISSION_UNCERTAIN,
            503 to ProviderErrorCode.SUBMISSION_UNCERTAIN,
        )) {
            val server = MockWebServer()
            server.enqueue(MockResponse().setResponseCode(status))
            server.start()
            try {
                val error = assertThrows(ProviderError::class.java) {
                    runBlocking {
                        GroqAdapter(providerHttp(server)).submit(
                            request(Provider.GROQ, GroqAdapter.MODEL_TURBO), "key", ResponseSpool {},
                        )
                    }
                }
                assertEquals(expected, error.code)
                assertEquals(status, error.httpStatus)
            } finally {
                server.shutdown()
            }
        }

        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.start()
        try {
            val error = assertThrows(ProviderError::class.java) {
                runBlocking {
                    GroqAdapter(providerHttp(server)).submit(
                        request(Provider.GROQ, GroqAdapter.MODEL_TURBO), "key", ResponseSpool {},
                    )
                }
            }
            assertEquals(ProviderErrorCode.SUBMISSION_UNCERTAIN, error.code)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun invalidInputsAreRejectedBeforeAnyUpload() {
        val server = MockWebServer()
        server.start()
        try {
            val unapproved = assertThrows(ProviderError::class.java) {
                runBlocking {
                    GroqAdapter(providerHttp(server)).submit(
                        request(
                            Provider.GROQ,
                            GroqAdapter.MODEL_TURBO,
                            uploadApproved = false,
                        ),
                        "key",
                        ResponseSpool {},
                    )
                }
            }
            assertEquals(ProviderErrorCode.INVALID_INPUT, unapproved.code)

            val invalidLanguage = assertThrows(ProviderError::class.java) {
                runBlocking {
                    GroqAdapter(providerHttp(server)).submit(
                        request(Provider.GROQ, GroqAdapter.MODEL_TURBO, language = "english"),
                        "key", ResponseSpool {},
                    )
                }
            }
            assertEquals(ProviderErrorCode.INVALID_INPUT, invalidLanguage.code)

            val invalidRegion = assertThrows(ProviderError::class.java) {
                runBlocking {
                    GroqAdapter(providerHttp(server)).submit(
                        request(Provider.GROQ, GroqAdapter.MODEL_TURBO, region = Region.EU),
                        "key", ResponseSpool {},
                    )
                }
            }
            assertEquals(ProviderErrorCode.UNSUPPORTED_OPTION, invalidRegion.code)

            val missingFile = assertThrows(ProviderError::class.java) {
                runBlocking {
                    GroqAdapter(providerHttp(server)).submit(
                        TranscriptionRequest(
                            audio = File("/definitely/missing/audio.wav"),
                            mimeType = "audio/wav",
                            config = JobConfig(
                                provider = Provider.GROQ,
                                model = GroqAdapter.MODEL_TURBO,
                                uploadApproved = true,
                            ),
                            durationMs = 1_000,
                        ),
                        "key", ResponseSpool {},
                    )
                }
            }
            assertEquals(ProviderErrorCode.INVALID_INPUT, missingFile.code)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun groqMinimumFileLengthIsRejectedBeforeUpload() {
        val server = MockWebServer()
        server.start()
        try {
            val error = assertThrows(ProviderError::class.java) {
                runBlocking {
                    GroqAdapter(providerHttp(server)).submit(
                        request(
                            Provider.GROQ,
                            GroqAdapter.MODEL_TURBO,
                            durationMs = GroqAdapter.MIN_DURATION_MS - 1,
                        ),
                        "key",
                        ResponseSpool {},
                    )
                }
            }
            assertEquals(ProviderErrorCode.INVALID_INPUT, error.code)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    private fun request(
        provider: Provider,
        model: String,
        language: String? = null,
        wordTimestamps: Boolean = false,
        segmentTimestamps: Boolean = true,
        diarization: Boolean = false,
        contextTerms: List<String> = emptyList(),
        region: Region = Region.US,
        chunkIndex: Int = 0,
        chunkStartMs: Long = 0,
        durationMs: Long = 2_000,
        uploadApproved: Boolean = true,
    ): TranscriptionRequest {
        val file = Files.createTempFile("sourcescribe-sync-", ".wav").toFile().apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
        return TranscriptionRequest(
            audio = file,
            mimeType = "audio/wav",
            config = JobConfig(
                mode = AcquisitionMode.STT_ONLY,
                provider = provider,
                model = model,
                region = region,
                language = language,
                diarization = diarization,
                wordTimestamps = wordTimestamps,
                segmentTimestamps = segmentTimestamps,
                contextTerms = contextTerms,
                uploadApproved = uploadApproved,
            ),
            chunkIndex = chunkIndex,
            chunkStartMs = chunkStartMs,
            durationMs = durationMs,
        )
    }

    private fun providerHttp(server: MockWebServer): ProviderHttp = ProviderHttp(
        OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url("/fixture")).build())
        }.build(),
    )

    private fun fixture(name: String): String =
        javaClass.getResource("/providers/sync/$name")!!.readText(StandardCharsets.UTF_8)
}
