package app.sourcescribe

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sourcescribe.core.*
import app.sourcescribe.core.providers.AssemblyAiAdapter
import app.sourcescribe.core.providers.GroqAdapter
import app.sourcescribe.data.AttemptRow
import app.sourcescribe.data.CredentialInfo
import app.sourcescribe.data.JobWaits
import app.sourcescribe.data.QueueReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewRulesTest {
    @Test
    fun deliberateStartBindsApprovalToModeCredentialProviderAndRegionWithoutChangingTheDraft() {
        val key = CredentialInfo("selected-key", Provider.GROQ, Region.US)
        val draft = JobConfig(mode = AcquisitionMode.STT_ONLY, provider = key.provider, region = key.region,
            credentialId = key.id, model = GroqAdapter.MODEL_TURBO, maxCostMicrousd = 123,
            audioTrackId = "chosen-audio", uploadApproved = false)
        for (mode in AcquisitionMode.entries) {
            val selected = draft.copy(mode = mode)
            assertEquals(selected.copy(uploadApproved = mode != AcquisitionMode.CAPTIONS_ONLY),
                MainViewModel.configurationForStart(selected, listOf(key)))
        }
        val staleApproval = draft.copy(uploadApproved = true)
        for (keys in listOf(emptyList(), listOf(key.copy(id = "other-key")),
            listOf(key.copy(provider = Provider.OPENAI)), listOf(key.copy(region = Region.EU)))) {
            assertEquals(draft, MainViewModel.configurationForStart(staleApproval, keys))
        }
        assertEquals(false, draft.uploadApproved)
    }

    @Test
    fun onlyTheAppsOwnCeilingStillStopsASourceFromBeingPriced() {
        // Since 0.4.0 the typed length limit is gone and the ceiling is the only number left, so two hours
        // — the length the old default of sixty minutes refused — is an ordinary source that gets a price.
        assertEquals(false, MainViewModel.sourceTooLong(7_200_000L))
        assertEquals(false, MainViewModel.sourceTooLong(JobLimits.MAX_AUDIO_SECONDS * 1000L))
        assertTrue(MainViewModel.sourceTooLong(JobLimits.MAX_AUDIO_SECONDS * 1000L + 1))

        // A source whose length nobody knows is not too long. It cannot start either, for another reason.
        assertEquals(false, MainViewModel.sourceTooLong(null))
    }

    @Test
    fun aStartedJobCarriesTheAppsCeilingWhateverTheDraftBroughtWithIt() {
        // A draft can still carry a lower limit: stored defaults, a preset, or a job an older version
        // created and that gets prepared again. There is no field left to raise it with, so a job that kept
        // it would refuse a source with nothing on the screen to change about it.
        val stale = JobConfig(mode = AcquisitionMode.STT_ONLY, provider = Provider.GROQ,
            model = GroqAdapter.MODEL_TURBO, maxAudioSeconds = 3_600L)

        assertEquals(JobLimits.MAX_AUDIO_SECONDS,
            MainViewModel.configurationForStart(stale, emptyList()).maxAudioSeconds)
        assertEquals(JobLimits.MAX_AUDIO_SECONDS, JobConfig().maxAudioSeconds)
        // And a source of two hours, which that stale limit refused, is startable again.
        val source = SourceResolver.youtube("https://www.youtube.com/watch?v=jNQXAC9IVRw").copy(durationMs = 7_200_000L)
        val id = requireNotNull(source.videoId)
        val audio = listOf(AudioTrack("251", id, "en", null, true, "fixture", codec = "opus", bitrateKbps = 160))
        val key = CredentialInfo("fixture-key", Provider.GROQ, Region.US)
        val resolved = ResolvedSource(source, emptyList(), audio, emptyMap())
        val config = stale.copy(credentialId = key.id, region = Region.US, audioTrackId = "251")
        assertEquals(null, MainViewModel.previewError(SourcePreview(resolved, config, null), listOf(key)))

        // A source whose length the metadata never stated cannot start, and says which of the two it is.
        assertEquals("SOURCE_DURATION_UNKNOWN", MainViewModel.previewError(
            SourcePreview(ResolvedSource(source.copy(durationMs = null), emptyList(), audio, emptyMap()), config, null),
            listOf(key),
        ))
        assertEquals("SOURCE_LONGER_THAN_LIMIT", MainViewModel.previewError(
            SourcePreview(
                ResolvedSource(source.copy(durationMs = JobLimits.MAX_AUDIO_SECONDS * 1000L + 1), emptyList(), audio, emptyMap()),
                config,
                null,
            ),
            listOf(key),
        ))
    }

    @Test
    fun aTermListTheProviderRefusesIsNamedAsAnErrorAndNotPriced() {
        val config = JobConfig(mode = AcquisitionMode.STT_ONLY, provider = Provider.ASSEMBLYAI,
            model = AssemblyAiAdapter.MODEL_U35, credentialId = "c", uploadApproved = true,
            maxAudioSeconds = 3_600L)

        // Both provider paths refuse a list over a single blank entry, so a blank one among real terms is
        // not a smaller prompt — it is a job that cannot start. DEFECTS 31 had the preview silent about
        // exactly this while the submission would answer INVALID_INPUT.
        assertEquals(null, MainViewModel.configError(config))
        assertEquals("CONTEXT_TERM_BLANK", MainViewModel.configError(config.copy(contextTerms = listOf(""))))
        assertEquals(
            "CONTEXT_TERM_BLANK",
            MainViewModel.configError(config.copy(contextTerms = listOf("Kubernetes", "  "))),
        )
        assertEquals(null, MainViewModel.configError(config.copy(contextTerms = listOf("Kubernetes"))))

        // And the cost row says nothing rather than quoting one, the way it says nothing for a source past
        // the limit. A figure in dollars above a line that refuses the run is the defect round 13 closed.
        val oneBlank = config.copy(contextTerms = listOf(""))
        assertEquals(null, MainViewModel.estimatedCostMicrousd(oneBlank, 3_600_000L))
        val mixed = config.copy(contextTerms = listOf("Kubernetes", ""))
        assertEquals(null, MainViewModel.estimatedCostMicrousd(mixed, 3_600_000L))
        // 260 004, not 260 000: the shown figure is the sum over the six chunks an hour is submitted in,
        // and each one rounds up on its own. The same hour through the per-chunk formula is 260 000 exactly,
        // which is why the two numbers differ by four and neither is a typo.
        assertEquals(
            260_004L,
            MainViewModel.estimatedCostMicrousd(config.copy(contextTerms = listOf("Kubernetes")), 3_600_000L),
        )

        // What a job gets is what Start makes of the preview, and Start drops blank entries. A list stored with
        // one, from before the field dropped them itself, showed as an empty field while the job stayed refused,
        // and nothing on the screen said where the blank entry was (round 17). Refused as it stands, it is neither
        // refused nor unpriced once Start has made the job of it.
        val key = CredentialInfo("c", Provider.ASSEMBLYAI, config.region)
        val started = MainViewModel.configurationForStart(oneBlank, listOf(key))
        assertEquals(emptyList<String>(), started.contextTerms)
        assertEquals(null, MainViewModel.configError(started))
        assertNotEquals(null, MainViewModel.estimatedCostMicrousd(started, 3_600_000L))
        assertEquals(listOf("Kubernetes"), MainViewModel.configurationForStart(mixed, listOf(key)).contextTerms)
    }

    @Test
    fun theResultScreenCallsAResultPartialByTheRuleItsExportsFollow() {
        val whole = TranscriptScope(requestedDurationMs = 2_000, processedIntervals = listOf(Interval(0, 2_000)),
            technicallyComplete = true)
        assertEquals(false, app.sourcescribe.ui.showsPartialNotice(whole))
        // No writer produces a confirmed scope with a missing chunk today, which is exactly why the screen may not
        // lean on that: the chunk is source nobody transcribed, whatever the flag beside it says. Until round 17
        // the screen asked `technicallyComplete != true` and would have called this one whole.
        assertTrue(app.sourcescribe.ui.showsPartialNotice(whole.copy(missingChunks = listOf(1))))
        assertTrue(app.sourcescribe.ui.showsPartialNotice(whole.copy(technicallyComplete = false)))
        assertTrue(app.sourcescribe.ui.showsPartialNotice(whole.copy(technicallyComplete = null)))
    }

    @Test
    fun onlyAQueuedJobLimitedToUnmeteredConnectionsSaysWhatItIsWaitingFor() {
        // The history line for a queued job read "result pending" whatever the job was waiting for, so a job
        // WorkManager holds until Wi-Fi returns looked exactly like one about to run. Only that combination
        // waits: a running job has already passed the constraint, a finished one is done, and a job that may
        // use any connection is not waiting for one.
        val unmetered = JobConfig(networkPolicy = NetworkPolicy.UNMETERED)
        for (state in ExecutionState.entries) {
            val attempts = listOf(queuedAttempt(Branch.STT, Phase.DOWNLOAD_AUDIO).copy(state = state))
            assertEquals("$state on unmetered only",
                if (state == ExecutionState.QUEUED) QueueReason.UNMETERED_CONNECTION else QueueReason.UNSTATED,
                JobWaits.reason(state, unmetered, attempts, SourceKind.YOUTUBE))
            assertEquals("$state on any connection", QueueReason.UNSTATED,
                JobWaits.reason(state, JobConfig(), attempts, SourceKind.YOUTUBE))
            // A stored configuration the app cannot read says nothing about the network either.
            assertEquals("$state without a configuration", QueueReason.UNSTATED,
                JobWaits.reason(state, null, attempts, SourceKind.YOUTUBE))
        }
    }

    @Test
    fun theUnmeteredSentenceAppearsOnlyForThePhasesThatAreReallyHeldByTheConstraint() {
        // Round 25, finding 1. The sentence was written for every queued job whose policy was UNMETERED, and
        // two ordinary ways into QUEUED have nothing to do with the connection: a phase `JobCoordinator.enqueue`
        // constrains to NOT_REQUIRED, and an attempt `SttStep` requeued because another job holds the audio or
        // the provider lock. Both read as "waiting for an unmetered connection" while the device was on Wi-Fi.
        val unmetered = JobConfig(networkPolicy = NetworkPolicy.UNMETERED)
        // What the coordinator enqueues under a network constraint, phase by phase, for each branch and for
        // both kinds of source. Written out rather than derived, so a change to `enqueue` has to be repeated
        // here deliberately instead of agreeing with itself.
        val constrained = mapOf(
            Triple(Branch.CAPTIONS, SourceKind.YOUTUBE, "captions") to
                Phase.entries - Phase.PERSIST,
            Triple(Branch.STT, SourceKind.YOUTUBE, "a downloaded source") to
                listOf(Phase.RESOLVE, Phase.DOWNLOAD_AUDIO, Phase.UPLOAD, Phase.SUBMIT, Phase.RETRIEVE),
            // An imported file is already on the device, so even resolving it needs nothing.
            Triple(Branch.STT, SourceKind.LOCAL_AUDIO, "an imported file") to
                listOf(Phase.DOWNLOAD_AUDIO, Phase.UPLOAD, Phase.SUBMIT, Phase.RETRIEVE),
        )
        for ((key, networkPhases) in constrained) {
            val (branch, kind, name) = key
            for (phase in Phase.entries) {
                val attempts = listOf(queuedAttempt(branch, phase))
                assertEquals("$name in $phase",
                    if (phase in networkPhases) QueueReason.UNMETERED_CONNECTION else QueueReason.UNSTATED,
                    JobWaits.reason(ExecutionState.QUEUED, unmetered, attempts, kind))
                assertEquals("$name in $phase needs the network at all", phase in networkPhases,
                    JobWaits.needsNetwork(branch, phase, kind))

                // The two local waits, in the phase each one is really raised in: `SttStep.prepare` claims the
                // audio lock in PREPARE_AUDIO, which needs no network anyway, and `SttStep.submit` claims the
                // provider lock in SUBMIT, which is constrained — so only the error tells the two apart there.
                for (code in listOf("AUDIO_RESOURCE_BUSY", "PROVIDER_RESOURCE_BUSY")) {
                    assertEquals("$name in $phase, queued on $code", QueueReason.ANOTHER_JOB,
                        JobWaits.reason(ExecutionState.QUEUED, unmetered,
                            listOf(queuedAttempt(branch, phase).copy(error = code, nextAt = 30_000L)), kind))
                }
                // Any other code a queued attempt carries is a retry delay it wrote itself; the connection is
                // not what it is waiting for either, and the card keeps that code's own sentence.
                assertEquals("$name in $phase, queued after an interrupted run", QueueReason.UNSTATED,
                    JobWaits.reason(ExecutionState.QUEUED, unmetered,
                        listOf(queuedAttempt(branch, phase).copy(error = "INTERRUPTED", nextAt = 30_000L)), kind))
            }
        }

        // Only the newest attempt of a branch counts, the set `JobCoordinator.summarize` reads for the job's
        // own state: a superseded attempt keeps the error it failed with and would otherwise decide the line.
        val superseded = queuedAttempt(Branch.STT, Phase.DOWNLOAD_AUDIO)
            .copy(id = "old", number = 1, error = "AUDIO_RESOURCE_BUSY")
        val current = queuedAttempt(Branch.STT, Phase.DOWNLOAD_AUDIO).copy(id = "new", number = 2)
        assertEquals(QueueReason.UNMETERED_CONNECTION,
            JobWaits.reason(ExecutionState.QUEUED, unmetered, listOf(superseded, current), SourceKind.YOUTUBE))
        // A branch that is not queued at all — finished, or waiting on the reader — says nothing about a wait.
        assertEquals(QueueReason.UNSTATED, JobWaits.reason(ExecutionState.QUEUED, unmetered,
            listOf(current.copy(state = ExecutionState.FINISHED)), SourceKind.YOUTUBE))
        // And a job whose rows have not been read yet claims nothing rather than the sentence it used to show.
        assertEquals(QueueReason.UNSTATED,
            JobWaits.reason(ExecutionState.QUEUED, unmetered, emptyList(), SourceKind.YOUTUBE))

        // Two branches, two waits: the connection is the one of them the reader can do something about.
        val locked = queuedAttempt(Branch.CAPTIONS, Phase.FETCH_CAPTIONS).copy(error = "PROVIDER_RESOURCE_BUSY")
        assertEquals(QueueReason.UNMETERED_CONNECTION,
            JobWaits.reason(ExecutionState.QUEUED, unmetered, listOf(locked, current), SourceKind.YOUTUBE))
        assertEquals(QueueReason.ANOTHER_JOB, JobWaits.reason(ExecutionState.QUEUED, unmetered,
            listOf(locked, current.copy(phase = Phase.PREPARE_AUDIO)), SourceKind.YOUTUBE))
    }

    private fun queuedAttempt(branch: Branch, phase: Phase) = AttemptRow(
        id = "attempt-$branch-$phase", jobId = "job", branch = branch, number = 1, createdAt = 0L,
        state = ExecutionState.QUEUED, phase = phase,
    )

    @Test
    fun aPercentageIsOnlyEverTheShareOfATotalTheAppReallyKnows() {
        assertEquals(0, app.sourcescribe.ui.percentOf(0, 100))
        assertEquals(33, app.sourcescribe.ui.percentOf(1, 3))
        assertEquals(100, app.sourcescribe.ui.percentOf(100, 100))
        // A number nobody can act on is not shown as a hundred and one percent, and a total of nothing is
        // not a division: both answer zero, and the line above the bar says what it is counting.
        assertEquals(100, app.sourcescribe.ui.percentOf(200, 100))
        assertEquals(0, app.sourcescribe.ui.percentOf(5, 0))
        assertEquals(0, app.sourcescribe.ui.percentOf(5, -1))
        // Two gigabytes times a hundred leaves an Int behind; the arithmetic stays in Long until the end.
        assertEquals(50, app.sourcescribe.ui.percentOf(1_000_000_000L, 2_000_000_000L))
        assertEquals(1, app.sourcescribe.ui.percentOf(21_474_837L, 2_000_000_000L))
    }

    @Test
    fun damagedStoredConfigurationHasNoFallbackProviderOrDefaults() {
        for (raw in listOf("", "{", "{\"mode\":\"BROKEN\"}", "{\"provider\":\"UNKNOWN\"}")) {
            assertNull(app.sourcescribe.data.decodeStoredJobConfig(raw))
        }
        val config = JobConfig(mode = AcquisitionMode.STT_ONLY, provider = Provider.GROQ,
            model = GroqAdapter.MODEL_TURBO, maxCostMicrousd = 123)
        assertEquals(config, app.sourcescribe.data.decodeStoredJobConfig(kotlinx.serialization.json.Json.encodeToString(config)))
    }

    @Test
    fun clipboardPartsPreserveEveryCharacterIncludingBoundaryEmoji() {
        val text = "a".repeat(63_999) + "\uD83D\uDE00" + "b".repeat(130_000)
        val ranges = MainViewModel.clipboardRanges(text)
        assertEquals(text, ranges.joinToString("") { text.substring(it) })
        assertTrue(ranges.all { it.count() <= 64_000 })
        assertTrue(ranges.none { text[it.last].isHighSurrogate() || text[it.first].isLowSurrogate() })
        assertTrue(MainViewModel.clipboardRanges("").isEmpty())
    }

    @Test
    fun startRequiresAvailableCaptionsAndExplicitAmbiguousAudioChoice() {
        // With a length, because since 0.4.0 a source without one cannot start for a reason of its own and
        // the two refusals would be told apart by nothing. A resolve always states one for a finished video.
        val source = SourceResolver.youtube("https://www.youtube.com/watch?v=jNQXAC9IVRw").copy(durationMs = 19_000L)
        val videoId = requireNotNull(source.videoId)
        val resolved = ResolvedSource(source, emptyList(), listOf(
            AudioTrack("a", videoId, "en", null, null, "fixture"),
            AudioTrack("b", videoId, "de", null, null, "fixture"),
        ), emptyMap())
        val captionOnly = SourcePreview(resolved, JobConfig(mode = AcquisitionMode.CAPTIONS_ONLY), null)
        assertEquals("NO_ACCEPTABLE_CAPTIONS", MainViewModel.previewError(captionOnly, emptyList()))
        // Caption-first remains available without permission to upload.
        assertNull(MainViewModel.previewError(captionOnly.copy(config = JobConfig()), emptyList()))
        val key = CredentialInfo("fixture-key", Provider.GROQ, Region.US)
        val stt = captionOnly.copy(config = JobConfig(mode = AcquisitionMode.STT_ONLY, provider = key.provider,
            model = GroqAdapter.MODEL_TURBO, credentialId = key.id, uploadApproved = false))
        assertEquals("CHOOSE_AUDIO_TRACK", MainViewModel.previewError(stt, listOf(key)))
        assertEquals("CREDENTIAL_REQUIRED", MainViewModel.previewError(stt, emptyList()))
        assertNull(MainViewModel.previewError(stt.copy(config = stt.config.copy(audioTrackId = "b")), listOf(key)))
    }
    @Test
    fun availableCaptionsDoNotRequireFallbackAudioSelection() {
        val source = SourceResolver.youtube("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        val id = requireNotNull(source.videoId)
        val caption = CaptionTrack("en", id, "en", null, "json3", Generation.UPLOADER_PROVIDED, Translation.NONE, "fixture")
        val resolved = ResolvedSource(source, listOf(caption), listOf(
            AudioTrack("a", id, "en", null, null, "fixture"), AudioTrack("b", id, "de", null, null, "fixture")), emptyMap())
        val key = CredentialInfo("fixture-key", Provider.GROQ, Region.US)
        val config = JobConfig(mode = AcquisitionMode.CAPTIONS_THEN_STT, provider = key.provider,
            credentialId = key.id, model = GroqAdapter.MODEL_TURBO, uploadApproved = true, captionTrackId = caption.id)
        assertNull(MainViewModel.previewError(SourcePreview(resolved, config, null), listOf(key)))
    }

    @Test
    fun everyErrorThePreviewCanShowHasItsHeightReserved() {
        val source = SourceResolver.youtube("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        val id = requireNotNull(source.videoId)
        val caption = CaptionTrack("en", id, "en", null, "json3", Generation.UPLOADER_PROVIDED, Translation.NONE, "fixture")
        val audio = listOf(AudioTrack("a", id, "en", null, null, "fixture"), AudioTrack("b", id, "de", null, null, "fixture"))
        val sources = listOf(emptyList(), listOf(caption)).flatMap { captions ->
            listOf(emptyList(), audio.take(1), audio).map { ResolvedSource(source, captions, it, emptyMap()) }
        }
        val options = listOf<(JobConfig) -> JobConfig>(
            { it }, { it.copy(diarization = true) }, { it.copy(wordTimestamps = true) }, { it.copy(segmentTimestamps = true) },
            { it.copy(contextTerms = listOf("Kubernetes")) }, { it.copy(contextTerms = listOf("Kubernetes", "")) },
            { it.copy(maxCostMicrousd = -1L) }, { it.copy(maxCostMicrousd = 1L) },
        )
        // Every mode, provider, model, region and source shape against each option, with and without a key.
        val seen = mutableSetOf<String>()
        for (mode in AcquisitionMode.entries) for (provider in listOf(null) + Provider.entries) {
            val models = listOf(null) + (provider?.let { MainViewModel.models(it) } ?: emptyList())
            for (model in models) for (region in Region.entries) for (option in options) for (resolved in sources) {
                val key = CredentialInfo("fixture-key", provider ?: Provider.GROQ, region)
                val config = option(JobConfig(mode = mode, provider = provider, model = model, region = region, credentialId = key.id))
                for (keys in listOf(emptyList(), listOf(key))) {
                    MainViewModel.previewError(SourcePreview(resolved, config, null), keys)?.let { seen += it }
                }
            }
        }
        assertEquals(
            "The preview card reserves room for the texts in PREVIEW_ERRORS_SHOWN_AS_TEXT. A code it can show " +
                "that the list lacks is still shown in full, but it moves the card when it appears. Add it there.",
            emptySet<String>(),
            seen - MainViewModel.PREVIEW_ERRORS_SHOWN_AS_TEXT.toSet() - "SOURCE_LONGER_THAN_LIMIT",
        )
        // The other direction, over the whole list: the grid has to reach every code it names, or the assertion
        // above is about nothing, and a code no preview can return reserves room for a text that never appears.
        // A hand-picked subset stood here until round 16, and one of the three codes it left out,
        // UPLOAD_APPROVAL_REQUIRED, turned out to be unreachable from the preview.
        assertEquals("Codes the grid failed to reach", emptyList<String>(),
            MainViewModel.PREVIEW_ERRORS_SHOWN_AS_TEXT.filterNot { it in seen })
    }

    @Test
    fun everyProviderRowNamesTheModelThatProviderWouldReallyRunWith() {
        // Item 25/7. The provider list says "Model: …" on every row, including the two the job is not using.
        // That claim has to be the model tapping the row would actually set, which is what `ConfigControls`
        // does: the first model of that provider. For the row that is selected it is the chosen model.
        val config = JobConfig(provider = Provider.GROQ, model = GroqAdapter.MODEL_V3)
        assertEquals(GroqAdapter.MODEL_V3, MainViewModel.modelInUse(config, Provider.GROQ))
        for (provider in Provider.entries - Provider.GROQ) {
            assertEquals(MainViewModel.models(provider).first(), MainViewModel.modelInUse(config, provider))
        }
        // Nothing chosen at all, and a model stored for a provider that no longer offers it — a preset or a
        // job an older version created. Neither is claimed to be in use, because a run would not use it.
        for (provider in Provider.entries) {
            assertEquals(MainViewModel.models(provider).first(), MainViewModel.modelInUse(JobConfig(), provider))
            assertEquals(MainViewModel.models(provider).first(),
                MainViewModel.modelInUse(JobConfig(provider = provider, model = "retired-model"), provider))
        }
    }

    @Test
    fun onlyACheckOfDifferentSourcesMovesTheViewToTheResults() {
        // Item 25/10. The screen scrolls to the results once per check. Every change to a track, an option or
        // the provider rebuilds the preview list with new configurations in it, and the key this scroll
        // watches has to ignore all of that, or working a control would throw the reader back to the top.
        val source = SourceResolver.youtube("https://www.youtube.com/watch?v=jNQXAC9IVRw").copy(durationMs = 19_000L)
        val id = requireNotNull(source.videoId)
        val audio = listOf(AudioTrack("251", id, "en", null, true, "fixture"),
            AudioTrack("140", id, "en", null, true, "fixture"))
        val resolved = ResolvedSource(source, emptyList(), audio, emptyMap())
        val first = SourcePreview(resolved, JobConfig(audioTrackId = "251"), null)
        val key = app.sourcescribe.ui.checkedSourcesKey(listOf(first))

        assertNotEquals(null, key)
        for (changed in listOf(
            first.copy(config = first.config.copy(audioTrackId = "140")),
            first.copy(config = first.config.copy(mode = AcquisitionMode.STT_ONLY, provider = Provider.GROQ)),
            first.copy(config = first.config.copy(contextTerms = listOf("Kubernetes"))),
            first.copy(previousJob = "an-earlier-job"),
        )) {
            assertEquals("a changed configuration must not count as a new check", key,
                app.sourcescribe.ui.checkedSourcesKey(listOf(changed)))
        }

        // A different source, a second source beside it, and no source at all are each a different answer.
        val other = SourceResolver.youtube("https://www.youtube.com/watch?v=dQw4w9WgXcQ").copy(durationMs = 19_000L)
        val second = SourcePreview(ResolvedSource(other, emptyList(), emptyList(), emptyMap()), JobConfig(), null)
        assertNotEquals(key, app.sourcescribe.ui.checkedSourcesKey(listOf(second)))
        assertNotEquals(key, app.sourcescribe.ui.checkedSourcesKey(listOf(first, second)))
        assertNull(app.sourcescribe.ui.checkedSourcesKey(emptyList()))
    }

    @Test
    fun aDraftChangeMovesTheEpochOfEveryTypedSettingItChangesButTheOneBeingTyped() {
        val before = JobConfig()
        for (setting in TypedSetting.entries) {
            // Exhaustive, so a setting added later cannot pass here without a change of its own.
            val after = when (setting) {
                TypedSetting.BUDGET -> before.copy(maxCostMicrousd = 1)
                TypedSetting.CAPTION_LANGUAGES -> before.copy(preferredLanguages = listOf("de"))
                TypedSetting.STT_LANGUAGE -> before.copy(language = "de")
                TypedSetting.CONTEXT_TERMS -> before.copy(contextTerms = listOf("Kubernetes"))
            }
            assertNotEquals(setting.name, setting.of(before), setting.of(after))
            val edits = DraftEdits()
            val fromOutside = edits.after(before, after, typed = null)
            val typed = edits.after(before, after, typed = setting)
            for (other in TypedSetting.entries) {
                assertEquals("$setting set from outside, epoch of $other moved", other == setting,
                    fromOutside.epoch(other) != edits.epoch(other))
                assertEquals("$setting typed, epoch of $other", edits.epoch(other), typed.epoch(other))
            }
            // Set back to where it started, the epoch does not return with it: text typed under the first
            // epoch must not become current again over a value it was never typed against.
            val back = fromOutside.after(after, before, typed = null)
            assertNotEquals(edits.epoch(setting), back.epoch(setting))
            assertNotEquals(fromOutside.epoch(setting), back.epoch(setting))
        }
        // A change to nothing a field shows moves nothing, so a half-typed entry survives a switch being flipped.
        val edits = DraftEdits()
        assertEquals(edits, edits.after(JobConfig(), JobConfig(retainRaw = true, diarization = true), typed = null))
        // Every view model draws its own session, so an epoch saved before the process died is not current after.
        assertNotEquals(DraftEdits().epoch(TypedSetting.BUDGET), DraftEdits().epoch(TypedSetting.BUDGET))
    }
}
