package app.sourcescribe.core

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class InvalidSource(val reason: String) : IllegalArgumentException(reason)

object SourceResolver {
    private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")
    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")

    fun youtube(input: String): Source {
        val value = input.trim()
        if (value.length > 8192 || value.any { it.isWhitespace() || it.code < 32 || it.code > 126 }) {
            throw InvalidSource("INVALID_URL")
        }
        val uri = try { URI(if ("://" in value) value else "https://$value") }
        catch (_: Exception) { throw InvalidSource("INVALID_URL") }
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port != -1 || uri.rawFragment?.contains('%') == true) {
            throw InvalidSource("INVALID_URL")
        }
        val host = uri.host?.lowercase() ?: throw InvalidSource("INVALID_HOST")
        if (host !in youtubeHosts && host != "youtu.be") throw InvalidSource("INVALID_HOST")
        if ('%' in (uri.rawPath ?: "") || '\\' in value) throw InvalidSource("INVALID_PATH")
        val params = try {
            uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map { part ->
                val pair = part.split('=', limit = 2)
                URLDecoder.decode(pair[0], StandardCharsets.UTF_8) to
                    URLDecoder.decode(pair.getOrElse(1) { "" }, StandardCharsets.UTF_8)
            }
        } catch (_: Exception) { throw InvalidSource("INVALID_QUERY") }
        if (params.count { it.first == "v" } > 1) throw InvalidSource("AMBIGUOUS_VIDEO")
        val path = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val id = when {
            host == "youtu.be" && path.size == 1 -> path.single()
            host in youtubeHosts && uri.path == "/watch" -> params.singleOrNull { it.first == "v" }?.second
            host in youtubeHosts && path.size == 2 && path[0] in setOf("shorts", "live", "embed") -> path[1]
            else -> null
        } ?: throw InvalidSource("EXPLICIT_VIDEO_REQUIRED")
        if (!videoIdPattern.matches(id)) throw InvalidSource("INVALID_VIDEO_ID")
        val queryId = params.singleOrNull { it.first == "v" }?.second
        if (queryId != null && queryId != id) throw InvalidSource("AMBIGUOUS_VIDEO")
        return Source(id = "youtube:$id", kind = SourceKind.YOUTUBE, videoId = id, canonicalUrl = "https://www.youtube.com/watch?v=$id")
    }

    fun sharedText(text: String): List<Source> {
        if (text.length > 32768) throw InvalidSource("INPUT_TOO_LARGE")
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val urls = tokens.filter { "://" in it || it.startsWith("youtu.be/") || it.startsWith("www.youtube.com/") || it.startsWith("youtube.com/") }
        if (urls.isEmpty()) throw InvalidSource("EXPLICIT_VIDEO_REQUIRED")
        if (urls.size > 20) throw InvalidSource("TOO_MANY_VIDEOS")
        return urls.map(::youtube).distinctBy { it.id }
    }

    fun requireMatchingVideo(source: Source, observedVideoId: String?) {
        if (source.kind != SourceKind.YOUTUBE || source.videoId == null || source.videoId != observedVideoId) {
            throw InvalidSource("SOURCE_ID_MISMATCH")
        }
    }
}
