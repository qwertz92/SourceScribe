package app.sourcescribe.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.net.URI

data class ResolvedSource(
    val source: Source,
    val captions: List<CaptionTrack>,
    val audio: List<AudioTrack>,
    // Ephemeral signed URLs never enter the persisted Source/track models.
    val captionUrls: Map<String, String>,
)

object ExtractorMetadata {
    private const val MAX_TRACK_BYTES = 64.0 * 1024 * 1024 * 1024
    private const val ORIGINAL_LANGUAGE_PREFERENCE = 10
    private const val AUDIO_DESCRIPTION_PREFERENCE = -10
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String, requested: Source): ResolvedSource {
        if (raw.length > 8 * 1024 * 1024) throw InvalidSource("METADATA_TOO_LARGE")
        val root = try { json.parseBounded(raw) as? JsonObject }
        catch (_: Exception) { null } ?: throw InvalidSource("INVALID_METADATA")
        // An empty value names nothing, here as much as inside a format below: a title or an original
        // language kept as "" would sit in the record and read as a fact somebody reported.
        fun string(name: String) = (root[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        SourceResolver.requireMatchingVideo(requested, string("id"))
        if (string("_type") in setOf("playlist", "multi_video") || string("live_status") in setOf("is_live", "is_upcoming")) {
            throw InvalidSource("LIVE_OR_PLAYLIST_UNSUPPORTED")
        }
        val duration = (root["duration"] as? JsonPrimitive)?.doubleOrNull
        if (duration != null && (!duration.isFinite() || duration < 0 || duration > 7 * 24 * 3600)) throw InvalidSource("INVALID_DURATION")
        val source = requested.copy(
            title = string("title")?.take(2000), channel = string("channel")?.take(1000),
            durationMs = duration?.times(1000)?.toLong(),
            // A date and a language tag each name one thing, so they follow the rule for identifiers below
            // rather than the one the title and the channel above follow. The address is refused too, and
            // for the same reason stated once more: half an address is not a shorter address but a wrong one.
            publishedDate = identifier(string("upload_date")),
            thumbnailUrl = string("thumbnail")?.takeIf { validImageUrl(it) },
            originalLanguage = identifier(string("language")),
        )
        val tracks = mutableListOf<CaptionTrack>()
        val urls = mutableMapOf<String, String>()
        for ((key, generation) in listOf("subtitles" to Generation.UPLOADER_PROVIDED, "automatic_captions" to Generation.AUTOMATIC)) {
            val languages = root[key] as? JsonObject ?: continue
            for ((language, formats) in languages) {
                if (!Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}").matches(language)) continue
                val choices = (formats as? JsonArray)?.filterIsInstance<JsonObject>() ?: continue
                val selected = listOf("json3", "vtt", "srt").firstNotNullOfOrNull { format ->
                    choices.lastOrNull { (it["ext"] as? JsonPrimitive)?.contentOrNull == format }
                } ?: continue
                val format = (selected["ext"] as JsonPrimitive).content
                val url = (selected["url"] as? JsonPrimitive)?.contentOrNull ?: throw InvalidSource("INVALID_CAPTION_URL")
                requireCaptionUrl(url)
                val segmented = URI(url).host == "manifest.googlevideo.com"
                val translated = "tlang" in captionParameters(url)
                val id = "$key:$language:$format"
                // The same rule as `string` above, and for the same reason: a name kept as "" is printed as
                // the reported name of the track, where the absent case prints `unknown` instead.
                val trackName = (selected["name"] as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.isNotBlank() }?.take(1000)
                tracks += CaptionTrack(id, requireNotNull(source.videoId), language, trackName, format,
                    generation, if (translated) Translation.AUTOMATIC else Translation.NONE,
                    "yt-dlp:$key;timedtext:tlang=${if (translated) "present" else "absent"}${if (segmented) ";hls-vtt-assembled" else ""}")
                urls[id] = url
            }
        }
        val audio = (root["formats"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { item ->
            // A key written as an empty string names nothing. Letting it through would put "" into the
            // record and then name the key in the provenance line for it.
            fun value(key: String) = (item[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            fun signedNumber(key: String) = (item[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
            fun number(key: String) = signedNumber(key)?.takeIf { it >= 0 }
            val id = value("format_id") ?: return@mapNotNull null
            val acodec = value("acodec")
            val vcodec = value("vcodec")
            if (!Regex("[A-Za-z0-9_.-]{1,80}").matches(id) || vcodec != "none" || acodec in setOf(null, "none")) return@mapNotNull null
            // A language tag, a codec and a container each name one thing; the note beside them is prose.
            // Both values are also kept as they arrived, because asking a question about a value is not the
            // same as carrying it. Whether a tag ends in `-desc` and whether a note says `(original)` can be
            // answered from the whole of what arrived; answering them from the bounded copy is how a marked
            // rendition would quietly lose its mark — and both marks decide which track is chosen.
            val statedLanguage = value("language")
            val language = identifier(statedLanguage)
            val statedNote = value("format_note")
            val note = statedNote?.take(500)
            val container = identifier(value("ext"), MAX_CONTAINER_LENGTH)
            val exact = number("filesize")?.takeIf { it > 0 && it <= MAX_TRACK_BYTES }?.toLong()
            val approximate = number("filesize_approx")?.takeIf { it > 0 && it <= MAX_TRACK_BYTES }?.toLong()
            val sampleRate = number("asr")?.takeIf { it in 1.0..768_000.0 }?.toInt()
            val channelCount = number("audio_channels")?.takeIf { it in 1.0..64.0 }?.toInt()
            // `abr` wins over `tbr` as soon as it holds a number at all, even where that number is then
            // rejected as implausible. That selection is left exactly as it was: which field supplies a
            // bitrate is a data change and does not belong inside a fix to the provenance line.
            val rateKey = if (number("abr") != null) "abr" else if (number("tbr") != null) "tbr" else null
            val rate = (number("abr") ?: number("tbr"))?.takeIf { it in 1.0..10_000.0 }?.toInt()
            // A DRC rendition is only ever marked by the extractor id suffix or its note; never inferred from bitrate.
            val compressed = id.endsWith("-drc", ignoreCase = true) || statedNote?.contains("drc", ignoreCase = true) == true
            // yt-dlp encodes the track role numerically: 10 original, 5 default, -10 audio description.
            val preference = signedNumber("language_preference")?.toInt()
            // Provenance is built from the values themselves instead of from a second set of checks beside
            // them: a field is named exactly when what it carried survived into this record. A key written as
            // JSON null, as an empty string, as a word where a number belongs, as a size of zero, or as a rate
            // outside the plausible range backed nothing. Where two keys can supply one fact, the one that did
            // not supply it stays unnamed.
            val backing = linkedMapOf(
                "language" to language,
                "language_preference" to preference,
                "format_note" to note,
                "acodec" to acodec,
                "vcodec" to vcodec,
                "ext" to container,
                "abr" to rate?.takeIf { rateKey == "abr" },
                "tbr" to rate?.takeIf { rateKey == "tbr" },
                "filesize" to exact,
                "filesize_approx" to approximate?.takeIf { exact == null },
                "asr" to sampleRate,
                "audio_channels" to channelCount,
            )
            AudioTrack(
                id = id,
                sourceVideoId = requireNotNull(source.videoId),
                language = language,
                name = note,
                isOriginal = when {
                    preference == ORIGINAL_LANGUAGE_PREFERENCE -> true
                    preference != null -> false
                    statedNote?.contains("(original)", ignoreCase = true) == true -> true
                    else -> null
                },
                evidence = "yt-dlp:formats." + backing.filterValues { it != null }.keys.joinToString(","),
                languageRefused = statedLanguage != null && language == null,
                codec = identifier(acodec, MAX_CODEC_LENGTH),
                container = container,
                bitrateKbps = rate,
                bytes = exact ?: approximate,
                bytesEstimated = exact == null && approximate != null,
                sampleRateHz = sampleRate,
                channels = channelCount,
                dynamicRangeCompressed = compressed,
                audioDescription = preference == AUDIO_DESCRIPTION_PREFERENCE ||
                    statedLanguage?.endsWith("-desc", ignoreCase = true) == true,
            )
        }.distinctBy { it.id }
        return ResolvedSource(source, tracks, audio, urls)
    }

    fun requireCaptionUrl(value: String) {
        val uri = try { URI(value) } catch (_: Exception) { throw InvalidSource("INVALID_CAPTION_URL") }
        val direct = uri.host in setOf("www.youtube.com", "youtube.com", "video.google.com") &&
            uri.path in setOf("/api/timedtext", "/timedtext")
        val segmented = uri.host == "manifest.googlevideo.com" &&
            uri.path?.startsWith("/api/manifest/hls_timedtext_playlist/") == true
        if (value.length > MAX_URL_LENGTH || uri.scheme != "https" || uri.rawUserInfo != null || uri.port != -1 ||
            uri.rawFragment != null || (!direct && !segmented)) throw InvalidSource("INVALID_CAPTION_URL")
    }

    /** Decode identity keys before comparing them; encoded duplicates must not bypass binding. */
    fun captionParameters(value: String): Map<String, String> {
        val parameters = linkedMapOf<String, String>()
        try {
            for (part in URI(value).rawQuery.orEmpty().split('&')) {
                val key = java.net.URLDecoder.decode(part.substringBefore('='), "UTF-8")
                if (key !in setOf("v", "lang", "tlang")) continue
                val content = java.net.URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
                if (parameters.put(key, content) != null) throw InvalidSource("INVALID_CAPTION_URL")
            }
        } catch (_: IllegalArgumentException) { throw InvalidSource("INVALID_CAPTION_URL") }
        return parameters
    }

    private fun validImageUrl(value: String): Boolean = try {
        val uri = URI(value)
        // Same length the caption address is held to, and for the same reason: an address is kept whole or
        // not at all, so the only place to stop an endless one is before it is accepted.
        value.length <= MAX_URL_LENGTH && uri.scheme == "https" && uri.rawUserInfo == null && uri.port == -1 &&
            uri.host in setOf("i.ytimg.com", "img.youtube.com")
    } catch (_: Exception) { false }

    /**
     * A value that names one thing is kept whole or not at all. Prose survives being shortened — a title cut
     * at two thousand characters still reads as the title — but a shortened date is a different date and a
     * shortened language tag a different tag. Worse, two of them can become one: `AudioTracks.automatic`
     * refuses to choose between two renditions whose languages differ, because which language is spoken is
     * the reader's decision, and two different tags sharing their first hundred characters would have made
     * that refusal quietly stop working.
     */
    private fun identifier(value: String?, maxLength: Int = MAX_IDENTIFIER_LENGTH) =
        value?.takeIf { it.length <= maxLength }

    internal const val MAX_URL_LENGTH = 32768
    private const val MAX_IDENTIFIER_LENGTH = 100
    private const val MAX_CODEC_LENGTH = 80
    private const val MAX_CONTAINER_LENGTH = 20
}

object TrackSelection {
    fun captions(resolved: ResolvedSource, config: JobConfig): List<CaptionTrack> {
        val eligible = resolved.captions.filter {
            (it.generation == Generation.UPLOADER_PROVIDED && config.allowUploaderCaptions ||
                it.generation == Generation.AUTOMATIC && config.allowAutomaticCaptions) &&
                (it.translation == Translation.NONE || config.allowTranslatedCaptions && it.translation == Translation.AUTOMATIC)
        }
        if (config.captionTrackId != null) return eligible.filter { it.id == config.captionTrackId }
        val languages = buildList {
            if (config.preferOriginalLanguage) resolved.source.originalLanguage?.let(::add)
            addAll(config.preferredLanguages)
        }.distinct()
        return eligible.sortedWith(compareBy<CaptionTrack>(
            { val index = languages.indexOfFirst { language -> it.language == language || it.language.startsWith("$language-") }; if (index < 0) Int.MAX_VALUE else index },
            { it.generation != Generation.UPLOADER_PROVIDED }, { it.translation != Translation.NONE }, { it.id },
        ))
    }

    fun audio(resolved: ResolvedSource, selectedId: String?): AudioTrack? {
        if (selectedId != null) return resolved.audio.singleOrNull { it.id == selectedId }
        return AudioTracks.automatic(resolved.audio)
    }
}
