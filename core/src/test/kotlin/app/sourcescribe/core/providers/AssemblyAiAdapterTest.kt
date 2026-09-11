package app.sourcescribe.core.providers

import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.PollResult
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderError
import app.sourcescribe.core.ProviderErrorCode
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.Region
import app.sourcescribe.core.RemoteHandle
import app.sourcescribe.core.ResponseSpool
import app.sourcescribe.core.Segment
import app.sourcescribe.core.SubmissionResult
import app.sourcescribe.core.TimeEvidence
import app.sourcescribe.core.TranscriptionRequest
import java.io.File
import java.util.Collections
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssemblyAiAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: AssemblyAiAdapter
    private lateinit var audio: File
    private val originalHosts = Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val original = chain.request()
            originalHosts += original.url.host
            val fixtureUrl = server.url(original.url.encodedPath)
            chain.proceed(original.newBuilder().url(fixtureUrl).build())
        }.build()
        adapter = AssemblyAiAdapter(ProviderHttp(client))
        audio = File.createTempFile("sourcescribe-assembly", ".wav").apply {
            writeBytes(byteArrayOf(0, 1, 2, 3, 4))
        }
    }

    @After
    fun tearDown() {
        audio.delete()
        server.shutdown()
    }

    @Test
    fun capabilitiesExposeOnlyExplicitModelsAndDatedPricing() {
        val u35 = adapter.capabilities(AssemblyAiAdapter.MODEL_U35)
        assertEquals(AssemblyAiAdapter.MODEL_U35, u35.model)
        assertEquals(AssemblyAiAdapter.PRICE_U35_MICRO_USD_PER_HOUR, u35.priceMicrousdPerHour)
        assertEquals("2026-09-07", u35.priceAsOf)
        assertEquals(setOf(Region.US, Region.EU), u35.regions)
        assertTrue(u35.remoteDeletion)
        assertFalse(u35.remoteCancellation)
        assertThrows(ProviderError::class.java) { adapter.capabilities("universal-3-pro") }
    }

    @Test
    fun completedSubmitStreamsUploadAndSendsOnlySelectedOptions() {
        enqueueFixture("upload.json")
        enqueueFixture("submit_completed.json")
        val request = request(
            config = baseConfig(
                model = AssemblyAiAdapter.MODEL_U35,
                language = "de",
                diarization = true,
                contextTerms = listOf("Kubernetes", "zero trust model"),
            ),
            chunkIndex = 2,
            chunkStartMs = 1_000,
        )
        val saved = mutableListOf<ByteArray>()

        val result = runBlocking {
            adapter.submit(request, "raw-test-key", ResponseSpool { saved += it })
        }

        val transcript = (result as SubmissionResult.Direct).transcript
        assertEquals(AssemblyAiAdapter.MODEL_U35, transcript.requestedModel)
        assertEquals("universal-2", transcript.reportedModel)
        assertEquals("en", transcript.language)
        assertEquals(listOf("en"), transcript.reportedLanguages)
        assertEquals(listOf("Hello", "world."), transcript.words.map(Segment::text))
        assertEquals(listOf(1_125L, 1_600L), transcript.words.mapNotNull(Segment::startMs))
        assertEquals(
            Segment(
                text = "Hello world.",
                startMs = 1_125,
                endMs = 2_200,
                speaker = "chunk-2:A",
                timeEvidence = TimeEvidence.PROVIDER_SEGMENT,
                chunkIndex = 2,
            ),
            transcript.segments.single(),
        )
        assertEquals(1, saved.size)
        assertEquals(fixture("submit_completed.json"), saved.single().decodeToString())

        val upload = server.takeRequest()
        assertEquals("POST", upload.method)
        assertEquals("/v2/upload", upload.path)
        assertEquals("raw-test-key", upload.getHeader("Authorization"))
        assertEquals("application/octet-stream", upload.getHeader("Content-Type"))
        assertEquals(byteArrayOf(0, 1, 2, 3, 4).toList(), upload.body.readByteArray().toList())

        val submit = server.takeRequest()
        assertEquals("POST", submit.method)
        assertEquals("/v2/transcript", submit.path)
        assertEquals("raw-test-key", submit.getHeader("Authorization"))
        val payload = Json.parseToJsonElement(submit.body.readUtf8()) as JsonObject
        assertEquals("https://cdn.assemblyai.com/upload/fixture-upload", payload["audio_url"]?.toString()?.trim('"'))
        assertEquals("[\"universal-3-5-pro\"]", payload["speech_models"].toString())
        assertEquals("de", payload["language_code"]?.toString()?.trim('"'))
        assertEquals("true", payload["speaker_labels"].toString())
        assertEquals("true", payload["punctuate"].toString())
        assertEquals("[\"Kubernetes\",\"zero trust model\"]", payload["keyterms_prompt"].toString())
        listOf("summarization", "redact_pii", "sentiment_analysis", "webhook_url", "language_detection").forEach {
            assertFalse(payload.containsKey(it))
        }
        assertEquals(listOf("api.assemblyai.com", "api.assemblyai.com"), originalHosts)
    }

    @Test
    fun automaticLanguageSelectionUsesDetectionWithoutLanguageCode() {
        enqueueFixture("upload.json")
        enqueueFixture("submit_queued.json")

        runBlocking {
            adapter.submit(request(), "raw-test-key", ResponseSpool { })
        }

        val upload = server.takeRequest()
        assertEquals("/v2/upload", upload.path)
        val submit = server.takeRequest()
        val payload = Json.parseToJsonElement(submit.body.readUtf8()) as JsonObject
        assertEquals("true", payload["language_detection"]?.toString())
        assertFalse(payload.containsKey("language_code"))
    }

    @Test
    fun regionalLanguageCodeUsesAssemblyAiUnderscoreForm() {
        enqueueFixture("upload.json")
        enqueueFixture("submit_queued.json")

        runBlocking {
            adapter.submit(
                request(baseConfig(language = "en-US")),
                "raw-test-key",
                ResponseSpool { },
            )
        }

        server.takeRequest()
        val payload = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals("en_us", payload["language_code"]?.toString()?.trim('"'))
        assertFalse(payload.containsKey("language_detection"))
    }

    @Test
    fun uploadRequiresExplicitApprovalBeforeAnyNetworkCall() {
        val error = assertThrows(ProviderError::class.java) {
            runBlocking {
                adapter.submit(
                    request(baseConfig().copy(uploadApproved = false)),
                    "key",
                    ResponseSpool { },
                )
            }
        }
        assertEquals(ProviderErrorCode.INVALID_INPUT, error.code)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun euQueuedPollCompleteAndDeleteStayOnEuApi() {
        enqueueFixture("upload.json")
        enqueueFixture("submit_queued.json")
        enqueueFixture("poll_processing.json")
        enqueueFixture("poll_completed.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val request = request(baseConfig(region = Region.EU))
        val saved = mutableListOf<ByteArray>()

        val submitted = runBlocking {
            adapter.submit(request, "eu-key", ResponseSpool { saved += it })
        }
        val handle = (submitted as SubmissionResult.Remote).handle
        assertEquals(RemoteHandle(Provider.ASSEMBLYAI, Region.EU, "0072a82b-aa22-4962-add2-6121c36c17c6"), handle)

        val waiting = runBlocking {
            adapter.poll(handle, request, "eu-key", ResponseSpool { saved += it })
        }
        assertEquals(PollResult.Waiting(), waiting)
        val complete = runBlocking {
            adapter.poll(handle, request, "eu-key", ResponseSpool { saved += it })
        }
        assertEquals("Hello world.", (complete as PollResult.Complete).transcript.segments.single().text)
        runBlocking { adapter.deleteRemote(handle, "eu-key") }

        assertEquals(3, saved.size)
        assertEquals(
            listOf(
                "api.eu.assemblyai.com",
                "api.eu.assemblyai.com",
                "api.eu.assemblyai.com",
                "api.eu.assemblyai.com",
                "api.eu.assemblyai.com",
            ),
            originalHosts,
        )
        assertEquals("/v2/upload", server.takeRequest().path)
        assertEquals("/v2/transcript", server.takeRequest().path)
        assertEquals("/v2/transcript/0072a82b-aa22-4962-add2-6121c36c17c6", server.takeRequest().path)
        assertEquals("/v2/transcript/0072a82b-aa22-4962-add2-6121c36c17c6", server.takeRequest().path)
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun rejectsUntrustedUploadUrlBeforePaidSubmit() {
        server.enqueue(MockResponse().setBody("{\"upload_url\":\"https://cdn.eu.assemblyai.com/upload/x\"}"))
        val error = assertThrows(ProviderError::class.java) {
            runBlocking { adapter.submit(request(), "key", ResponseSpool { }) }
        }
        assertEquals(ProviderErrorCode.INVALID_RESPONSE, error.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun validatesModelOptionsAndRegionBeforeNetwork() {
        val unsupported = assertThrows(ProviderError::class.java) {
            runBlocking {
                adapter.submit(request(baseConfig(model = "universal-3-pro")), "key", ResponseSpool { })
            }
        }
        assertEquals(ProviderErrorCode.UNSUPPORTED_OPTION, unsupported.code)
        assertEquals(0, server.requestCount)

        val mismatchedRegion = assertThrows(ProviderError::class.java) {
            runBlocking {
                adapter.poll(
                    RemoteHandle(Provider.ASSEMBLYAI, Region.EU, "0072a82b-aa22-4962-add2-6121c36c17c6"),
                    request(baseConfig(region = Region.US)),
                    "key",
                    ResponseSpool { },
                )
            }
        }
        assertEquals(ProviderErrorCode.INVALID_INPUT, mismatchedRegion.code)
        assertEquals(0, server.requestCount)

        val badId = assertThrows(ProviderError::class.java) {
            runBlocking {
                adapter.poll(
                    RemoteHandle(Provider.ASSEMBLYAI, Region.US, "not-a-uuid"),
                    request(),
                    "key",
                    ResponseSpool { },
                )
            }
        }
        assertEquals(ProviderErrorCode.INVALID_INPUT, badId.code)
        assertEquals(0, server.requestCount)

        val unsupportedLanguage = assertThrows(ProviderError::class.java) {
            runBlocking {
                adapter.submit(
                    request(
                        baseConfig(model = AssemblyAiAdapter.MODEL_U35, language = "ru"),
                    ),
                    "key",
                    ResponseSpool { },
                )
            }
        }
        assertEquals(ProviderErrorCode.INVALID_INPUT, unsupportedLanguage.code)

        val malformedLanguage = assertThrows(ProviderError::class.java) {
            runBlocking {
                adapter.submit(request(baseConfig(language = "EN")), "key", ResponseSpool { })
            }
        }
        assertEquals(ProviderErrorCode.INVALID_INPUT, malformedLanguage.code)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun rejectsTermAndDurationLimitsBeforeUpload() {
        val tooManyTerms = assertThrows(ProviderError::class.java) {
            runBlocking {
                adapter.submit(
                    request(baseConfig(contextTerms = List(201) { "term" })),
                    "key",
                    ResponseSpool { },
                )
            }
        }
        assertEquals(ProviderErrorCode.INVALID_INPUT, tooManyTerms.code)
        assertEquals(0, server.requestCount)

        val tooManyWords = assertThrows(ProviderError::class.java) {
            runBlocking {
                adapter.submit(
                    request(baseConfig(contextTerms = listOf("one two three four five six seven"))),
                    "key",
                    ResponseSpool { },
                )
            }
        }
        assertEquals(ProviderErrorCode.INVALID_INPUT, tooManyWords.code)
        assertEquals(0, server.requestCount)

        val tooLong = assertThrows(ProviderError::class.java) {
            runBlocking {
                adapter.submit(request(durationMs = AssemblyAiAdapter.MAX_DURATION_MS + 1), "key", ResponseSpool { })
            }
        }
        assertEquals(ProviderErrorCode.INVALID_INPUT, tooLong.code)
        assertEquals(0, server.requestCount)

        val tooShort = assertThrows(ProviderError::class.java) {
            runBlocking {
                adapter.submit(
                    request(durationMs = AssemblyAiAdapter.MIN_DURATION_MS - 1),
                    "key",
                    ResponseSpool { },
                )
            }
        }
        assertEquals(ProviderErrorCode.INVALID_INPUT, tooShort.code)
        assertEquals(0, server.requestCount)

        val nonAudioMime = assertThrows(ProviderError::class.java) {
            runBlocking {
                adapter.submit(
                    request().copy(mimeType = "video/mp4"),
                    "key",
                    ResponseSpool { },
                )
            }
        }
        assertEquals(ProviderErrorCode.INVALID_INPUT, nonAudioMime.code)
        assertEquals(0, server.requestCount)

        val overflow = assertThrows(ProviderError::class.java) {
            adapter.parseSavedResponse(
                fixture("submit_completed.json").toByteArray(),
                request(chunkStartMs = Long.MAX_VALUE, durationMs = 1),
            )
        }
        assertEquals(ProviderErrorCode.INVALID_INPUT, overflow.code)

        val replayTooLong = assertThrows(ProviderError::class.java) {
            adapter.parseSavedResponse(
                fixture("submit_completed.json").toByteArray(),
                request(durationMs = AssemblyAiAdapter.MAX_DURATION_MS + 1),
            )
        }
        assertEquals(ProviderErrorCode.INVALID_INPUT, replayTooLong.code)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun missingRequestedWordsOrSpeakersPreserveTextAndMarkResultIncomplete() {
        val wordsMissing = (adapter.parseSavedResponse(
            fixture("completed_missing_words.json").toByteArray(),
            request(config = baseConfig(wordTimestamps = true)),
        ) as SubmissionResult.Direct).transcript
        assertEquals("No spoken words.", wordsMissing.segments.single().text)
        assertTrue(wordsMissing.words.isEmpty())
        assertFalse(wordsMissing.technicallyComplete)
        assertTrue(wordsMissing.warnings.contains("WORD_TIMESTAMPS_MISSING"))

        val speakerMissing = (adapter.parseSavedResponse(
            fixture("completed_missing_speakers.json").toByteArray(),
            request(config = baseConfig(diarization = true)),
        ) as SubmissionResult.Direct).transcript
        assertEquals("hello there", speakerMissing.segments.single().text)
        assertNull(speakerMissing.segments.single().speaker)
        assertEquals(listOf("hello", "there"), speakerMissing.words.map(Segment::text))
        assertFalse(speakerMissing.technicallyComplete)
        assertTrue(speakerMissing.warnings.contains("DIARIZATION_MISSING"))
    }

    @Test
    fun malformedWordEntriesKeepValidWordsAndMarkResultIncomplete() {
        val transcript = (adapter.parseSavedResponse(
            """
                {
                  "id":"0072a82b-aa22-4962-add2-6121c36c17c6",
                  "status":"completed",
                  "text":"one two",
                  "language_code":"en",
                  "words":[
                    {"text":"one","start":100,"end":250},
                    {"text":"broken","start":300}
                  ],
                  "utterances":null
                }
            """.trimIndent().toByteArray(),
            request(config = baseConfig(wordTimestamps = true, segmentTimestamps = false)),
        ) as SubmissionResult.Direct).transcript
        assertEquals(listOf("one"), transcript.words.map(Segment::text))
        assertEquals("one two", transcript.segments.single().text)
        assertFalse(transcript.technicallyComplete)
        assertTrue(transcript.warnings.any { it.startsWith("WORD_TIMESTAMPS_MALFORMED") })
    }

    @Test
    fun responseTimesStayWithinChunkAndOversizedReplayIsRejected() {
        val wordTranscript = (adapter.parseSavedResponse(
            """
                {
                  "id":"0072a82b-aa22-4962-add2-6121c36c17c6",
                  "status":"completed",
                  "text":"one two",
                  "language_code":"en",
                  "words":[
                    {"text":"one","start":0,"end":160},
                    {"text":"two","start":160,"end":161}
                  ],
                  "utterances":null
                }
            """.trimIndent().toByteArray(),
            request(
                config = baseConfig(wordTimestamps = true, segmentTimestamps = false),
                durationMs = AssemblyAiAdapter.MIN_DURATION_MS,
            ),
        ) as SubmissionResult.Direct).transcript
        assertEquals("one two", wordTranscript.segments.single().text)
        assertEquals(listOf("one"), wordTranscript.words.map(Segment::text))
        assertFalse(wordTranscript.technicallyComplete)
        assertTrue(wordTranscript.warnings.any { it.startsWith("WORD_TIMESTAMPS_OUT_OF_RANGE") })

        val utteranceTranscript = (adapter.parseSavedResponse(
            """
                {
                  "id":"0072a82b-aa22-4962-add2-6121c36c17c6",
                  "status":"completed",
                  "text":"hello there",
                  "language_code":"en",
                  "words":null,
                  "utterances":[
                    {"text":"hello","start":0,"end":100,"speaker":"A"},
                    {"text":"there","start":100,"end":161,"speaker":"B"}
                  ]
                }
            """.trimIndent().toByteArray(),
            request(
                config = baseConfig(diarization = true),
                durationMs = AssemblyAiAdapter.MIN_DURATION_MS,
            ),
        ) as SubmissionResult.Direct).transcript
        assertEquals("hello there", utteranceTranscript.segments.single().text)
        assertNull(utteranceTranscript.segments.single().startMs)
        assertNull(utteranceTranscript.segments.single().endMs)
        assertEquals(TimeEvidence.UNKNOWN, utteranceTranscript.segments.single().timeEvidence)
        assertFalse(utteranceTranscript.technicallyComplete)
        assertTrue(utteranceTranscript.warnings.any { it.startsWith("DIARIZATION_OUT_OF_RANGE") })

        val oversized = ByteArray(16 * 1024 * 1024 + 1) { ' '.code.toByte() }
        val error = assertThrows(ProviderError::class.java) {
            adapter.parseSavedResponse(oversized, request())
        }
        assertEquals(ProviderErrorCode.INVALID_RESPONSE, error.code)
    }

    @Test
    fun missingRequestedSegmentsKeepTextWithoutInventedWordExtent() {
        val transcript = (adapter.parseSavedResponse(
            """
                {
                  "id":"0072a82b-aa22-4962-add2-6121c36c17c6",
                  "status":"completed",
                  "text":"one",
                  "language_code":"en",
                  "words":[{"text":"one","start":0,"end":160}],
                  "utterances":null
                }
            """.trimIndent().toByteArray(),
            request(config = baseConfig(wordTimestamps = true, segmentTimestamps = true)),
        ) as SubmissionResult.Direct).transcript

        assertEquals("one", transcript.segments.single().text)
        assertNull(transcript.segments.single().startMs)
        assertNull(transcript.segments.single().endMs)
        assertEquals(TimeEvidence.UNKNOWN, transcript.segments.single().timeEvidence)
        assertEquals(listOf("one"), transcript.words.map(Segment::text))
        assertFalse(transcript.technicallyComplete)
        assertTrue(transcript.warnings.contains("SEGMENT_TIMESTAMPS_MISSING"))
    }

    @Test
    fun mapsAuthRateLimitAndRetryAfterWithoutExposingBody() {
        listOf(
            401 to ProviderErrorCode.AUTHENTICATION,
            403 to ProviderErrorCode.ACCESS_DENIED,
            429 to ProviderErrorCode.RATE_LIMIT,
        ).forEach { (status, expectedCode) ->
            server.enqueue(MockResponse().setBody(fixture("upload.json")))
            server.enqueue(
                MockResponse()
                    .setResponseCode(status)
                    .setHeader("Retry-After", "17")
                    .setBody("provider secret detail"),
            )
            val error = assertThrows(ProviderError::class.java) {
                runBlocking { adapter.submit(request(), "key", ResponseSpool { }) }
            }
            assertEquals(expectedCode, error.code)
            assertEquals(17L, error.retryAfterSeconds)
            assertNull(error.message?.takeIf { it.contains("provider secret", ignoreCase = true) })
        }
    }

    @Test
    fun submitDisconnectIsUncertainAndDoesNotReplayAutomatically() {
        enqueueFixture("upload.json")
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST),
        )
        val error = assertThrows(ProviderError::class.java) {
            runBlocking { adapter.submit(request(), "key", ResponseSpool { }) }
        }
        assertEquals(ProviderErrorCode.SUBMISSION_UNCERTAIN, error.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun savedQueuedAndCompletedResponsesReplayWithoutSubmission() {
        val request = request(chunkStartMs = 5_000)
        val queued = adapter.parseSavedResponse(fixture("submit_queued.json").toByteArray(), request)
        assertEquals(SubmissionResult.Remote(RemoteHandle(Provider.ASSEMBLYAI, Region.US, "0072a82b-aa22-4962-add2-6121c36c17c6")), queued)
        val completed = adapter.parseSavedResponse(fixture("submit_completed.json").toByteArray(), request)
        assertEquals("Hello world.", (completed as SubmissionResult.Direct).transcript.segments.single().text)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun badJsonMissingTextAndRemoteErrorAreTypedAndSafe() {
        val request = request()
        val badJson = assertThrows(ProviderError::class.java) {
            adapter.parseSavedResponse("not-json".toByteArray(), request)
        }
        assertEquals(ProviderErrorCode.INVALID_RESPONSE, badJson.code)

        val missingText = assertThrows(ProviderError::class.java) {
            adapter.parseSavedResponse(
                "{\"id\":\"0072a82b-aa22-4962-add2-6121c36c17c6\",\"status\":\"completed\"}".toByteArray(),
                request,
            )
        }
        assertEquals(ProviderErrorCode.INVALID_RESPONSE, missingText.code)

        val emptyText = assertThrows(ProviderError::class.java) {
            adapter.parseSavedResponse(
                "{\"id\":\"0072a82b-aa22-4962-add2-6121c36c17c6\",\"status\":\"completed\",\"text\":\" \"}".toByteArray(),
                request,
            )
        }
        assertEquals(ProviderErrorCode.INVALID_RESPONSE, emptyText.code)

        val remoteError = assertThrows(ProviderError::class.java) {
            adapter.parseSavedResponse(fixture("poll_error.json").toByteArray(), request)
        }
        assertEquals(ProviderErrorCode.REMOTE_FAILED, remoteError.code)
        assertEquals("REMOTE_FAILED", remoteError.message)
    }

    @Test
    fun anAnswerFullOfBrokenEntriesCannotGrowTheWarningListWithIt() {
        // The cap itself has its own unit test; this one covers the wiring, because a warning list that is
        // collected in the adapter and capped somewhere else would look correct in both places on its own.
        // Two hundred unusable language entries produce two hundred distinct warnings before the cap.
        val languages = List(200) { "0" }.joinToString(",")
        val transcript = (adapter.parseSavedResponse(
            """
                {
                  "id":"0072a82b-aa22-4962-add2-6121c36c17c6",
                  "status":"completed",
                  "text":"one two",
                  "language_codes":[$languages]
                }
            """.trimIndent().toByteArray(),
            request(config = baseConfig(wordTimestamps = true, segmentTimestamps = false)),
        ) as SubmissionResult.Direct).transcript

        assertEquals(Warnings.LIMIT + 1, transcript.warnings.size)
        assertEquals("REPORTED_LANGUAGES_MALFORMED_0", transcript.warnings.first())
        assertEquals(Warnings.TRUNCATED, transcript.warnings.last())
        assertFalse(transcript.warnings.contains("REPORTED_LANGUAGES_MALFORMED_${Warnings.LIMIT}"))
        // The marker is what keeps a shortened list honest: the missing word timestamps of this answer did
        // not fit into it any more, and without the marker the list would read as the whole picture.
        assertFalse(transcript.warnings.contains("WORD_TIMESTAMPS_MISSING"))
        // Completeness is decided separately from the warning list, so the cap cannot talk a partial
        // result into looking complete.
        assertFalse(transcript.technicallyComplete)
        assertEquals(emptyList<String>(), transcript.reportedLanguages)
    }

    @Test
    fun aReportedLanguageIsTakenOnlyWhereItLooksLikeALanguageTag() {
        fun parsed(reported: String): Pair<String?, List<String>> {
            val response = """
                {
                  "id":"0072a82b-aa22-4962-add2-6121c36c17c6",
                  "status":"completed",
                  "text":"one",
                  "language_code":$reported,
                  "words":[],
                  "utterances":null
                }
            """.trimIndent()
            val transcript = (adapter.parseSavedResponse(response.toByteArray(), request())
                as SubmissionResult.Direct).transcript
            return transcript.language to transcript.warnings
        }

        // The shapes AssemblyAI actually reports stay untouched.
        assertEquals("en", parsed("\"en\"").first)
        assertEquals("en_us", parsed("\"en_us\"").first)
        assertEquals("zh-CN", parsed("\"zh-CN\"").first)

        // Anything else is stored with the transcript and shown as its language, so it is refused loudly
        // instead of taken at whatever length and character set an answer happens to carry.
        val long = parsed("\"" + "x".repeat(400) + "\"")
        assertNull(long.first)
        assertTrue(long.second.toString(), "REPORTED_LANGUAGES_MALFORMED" in long.second)
        val prose = parsed("\"English (United States)\"")
        assertNull(prose.first)
        assertTrue(prose.second.toString(), "REPORTED_LANGUAGES_MALFORMED" in prose.second)
    }

    @Test
    fun wordTimesAreMillisecondsAndChunkOffsetAppliesOnlyToEvidence() {
        val response = """
            {
              "id":"0072a82b-aa22-4962-add2-6121c36c17c6",
              "status":"completed",
              "text":"one two",
              "language_code":"en",
              "speech_model_used":"universal-2",
              "words":[
                {"text":"one","start":100,"end":250,"speaker":"A"},
                {"text":"two","start":300,"end":500,"speaker":"A"}
              ],
              "utterances":null
            }
        """.trimIndent()
        val transcript = (adapter.parseSavedResponse(
            response.toByteArray(),
            request(
                config = baseConfig(wordTimestamps = true, segmentTimestamps = false),
                chunkStartMs = 2_000,
            ),
        ) as SubmissionResult.Direct).transcript
        assertEquals(1, transcript.segments.size)
        assertEquals("one two", transcript.segments.single().text)
        assertEquals(2_100L, transcript.segments.single().startMs)
        assertEquals(2_500L, transcript.segments.single().endMs)
        assertEquals(TimeEvidence.PROVIDER_WORD, transcript.segments.single().timeEvidence)
        assertEquals(listOf("one", "two"), transcript.words.map(Segment::text))
        assertEquals(listOf(2_100L, 2_300L), transcript.words.mapNotNull(Segment::startMs))
        assertTrue(transcript.words.all { it.timeEvidence == TimeEvidence.PROVIDER_WORD })
        assertTrue(transcript.words.all { it.chunkIndex == 0 })
        assertTrue(transcript.words.all { it.speaker == "chunk-0:A" })
    }

    @Test
    fun speakerIdsAndEvidenceStayScopedAcrossCombinedChunks() {
        val raw = fixture("submit_completed.json").toByteArray()
        val transcripts = (0..1).map { chunkIndex ->
            (adapter.parseSavedResponse(
                raw,
                request(
                    config = baseConfig(diarization = true, wordTimestamps = true),
                    chunkIndex = chunkIndex,
                    chunkStartMs = chunkIndex * 2_000L,
                ),
            ) as SubmissionResult.Direct).transcript
        }
        val segments = transcripts.flatMap { it.segments }
        val words = transcripts.flatMap { it.words }

        assertEquals(listOf("Hello world.", "Hello world."), segments.map(Segment::text))
        assertEquals(listOf("chunk-0:A", "chunk-1:A"), segments.map(Segment::speaker))
        assertEquals(listOf(0, 1), segments.map(Segment::chunkIndex))
        assertEquals(listOf("Hello", "world.", "Hello", "world."), words.map(Segment::text))
        assertEquals(
            listOf("chunk-0:A", "chunk-0:A", "chunk-1:A", "chunk-1:A"),
            words.map(Segment::speaker),
        )
        assertEquals(listOf(0, 0, 1, 1), words.map(Segment::chunkIndex))
    }

    @Test
    fun mismatchedPollIdIsRejectedAfterRawResponseIsSpooled() {
        server.enqueue(
            MockResponse().setBody(
                "{\"id\":\"1072a82b-aa22-4962-add2-6121c36c17c6\",\"status\":\"processing\",\"text\":null}",
            ),
        )
        val saved = mutableListOf<ByteArray>()
        val error = assertThrows(ProviderError::class.java) {
            runBlocking {
                adapter.poll(
                    RemoteHandle(Provider.ASSEMBLYAI, Region.US, "0072a82b-aa22-4962-add2-6121c36c17c6"),
                    request(),
                    "key",
                    ResponseSpool { saved += it },
                )
            }
        }
        assertEquals(ProviderErrorCode.INVALID_RESPONSE, error.code)
        assertEquals(1, saved.size)
    }

    private fun request(
        config: JobConfig = baseConfig(),
        chunkIndex: Int = 0,
        chunkStartMs: Long = 0,
        durationMs: Long = 2_000,
    ) = TranscriptionRequest(
        audio = audio,
        mimeType = "audio/wav",
        config = config,
        chunkIndex = chunkIndex,
        chunkStartMs = chunkStartMs,
        durationMs = durationMs,
    )

    private fun baseConfig(
        model: String = AssemblyAiAdapter.MODEL_U2,
        region: Region = Region.US,
        language: String? = null,
        diarization: Boolean = false,
        contextTerms: List<String> = emptyList(),
        wordTimestamps: Boolean = false,
        segmentTimestamps: Boolean = true,
    ) = JobConfig(
        provider = Provider.ASSEMBLYAI,
        model = model,
        region = region,
        language = language,
        diarization = diarization,
        contextTerms = contextTerms,
        wordTimestamps = wordTimestamps,
        segmentTimestamps = segmentTimestamps,
        uploadApproved = true,
    )

    private fun enqueueFixture(name: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(fixture(name)))
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/providers/assembly/$name")).readText()
}
