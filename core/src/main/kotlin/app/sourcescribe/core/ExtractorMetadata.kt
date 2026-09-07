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
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String, requested: Source): ResolvedSource {
        if (raw.length > 8 * 1024 * 1024) throw InvalidSource("METADATA_TOO_LARGE")
        val root = try { json.parseBounded(raw) as? JsonObject }
        catch (_: Exception) { null } ?: throw InvalidSource("INVALID_METADATA")
        fun string(name: String) = (root[name] as? JsonPrimitive)?.contentOrNull
        SourceResolver.requireMatchingVideo(requested, string("id"))
        if (string("_type") in setOf("playlist", "multi_video") || string("live_status") in setOf("is_live", "is_upcoming")) {
            throw InvalidSource("LIVE_OR_PLAYLIST_UNSUPPORTED")
        }
        val duration = (root["duration"] as? JsonPrimitive)?.doubleOrNull
        if (duration != null && (!duration.isFinite() || duration < 0 || duration > 7 * 24 * 3600)) throw InvalidSource("INVALID_DURATION")
        val source = requested.copy(
            title = string("title")?.take(2000), channel = string("channel")?.take(1000),
            durationMs = duration?.times(1000)?.toLong(), publishedDate = string("upload_date"),
            thumbnailUrl = string("thumbnail")?.takeIf { validImageUrl(it) }, originalLanguage = string("language"),
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
                val translated = URI(url).rawQuery.orEmpty().split('&').any { it.substringBefore('=') == "tlang" }
                val id = "$key:$language:$format"
                tracks += CaptionTrack(id, requireNotNull(source.videoId), language,
                    (selected["name"] as? JsonPrimitive)?.contentOrNull?.take(1000), format,
                    generation, if (translated) Translation.AUTOMATIC else Translation.NONE,
                    "yt-dlp:$key;timedtext:tlang=${if (translated) "present" else "absent"}${if (segmented) ";hls-vtt-assembled" else ""}")
                urls[id] = url
            }
        }
        val audio = (root["formats"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { item ->
            fun value(key: String) = (item[key] as? JsonPrimitive)?.contentOrNull
            val id = value("format_id") ?: return@mapNotNull null
            if (!Regex("[A-Za-z0-9_.-]{1,80}").matches(id) || value("vcodec") != "none" || value("acodec") in setOf(null, "none")) return@mapNotNull null
            val note = value("format_note")?.take(500)
            AudioTrack(id, requireNotNull(source.videoId), value("language"), note,
                if (note?.contains("(original)", ignoreCase = true) == true) true else null,
                "yt-dlp:formats.language,format_note")
        }.distinctBy { it.id }
        return ResolvedSource(source, tracks, audio, urls)
    }

    fun requireCaptionUrl(value: String) {
        val uri = try { URI(value) } catch (_: Exception) { throw InvalidSource("INVALID_CAPTION_URL") }
        val direct = uri.host in setOf("www.youtube.com", "youtube.com", "video.google.com") &&
            uri.path in setOf("/api/timedtext", "/timedtext")
        val segmented = uri.host == "manifest.googlevideo.com" &&
            uri.path?.startsWith("/api/manifest/hls_timedtext_playlist/") == true
        if (value.length > 32768 || uri.scheme != "https" || uri.rawUserInfo != null || uri.port != -1 ||
            uri.rawFragment != null || (!direct && !segmented)) throw InvalidSource("INVALID_CAPTION_URL")
    }

    private fun validImageUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme == "https" && uri.rawUserInfo == null && uri.port == -1 && uri.host in setOf("i.ytimg.com", "img.youtube.com")
    } catch (_: Exception) { false }
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
        val originals = resolved.audio.filter { it.isOriginal == true }
        val candidates = originals.ifEmpty { resolved.audio }
        // Same language/note codecs are formats of one observed track, not separate voices.
        if (candidates.map { it.language to it.name?.replace(Regex("(?i)(tiny|low|medium|high)"), "")?.trim() }.distinct().size > 1) return null
        return candidates.firstOrNull()
    }
}
