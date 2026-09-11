package app.sourcescribe.core

/**
 * Turns extractor format ids such as `251` or `140-drc` into facts a reader can act on.
 *
 * The pipeline re-encodes every chosen rendition to mono 16 kHz MP3 at 64 kbit/s before any provider
 * sees it (see AudioPreparation), so a larger rendition adds download volume and time without adding
 * transcription detail. The recommendation therefore takes the smallest rendition of the wanted language,
 * with two exceptions that would cost transcription quality: a dynamic-range-compressed rendition loses
 * to a plain one, and a rendition below MINIMUM_USEFUL_KBPS loses to any normal one.
 */
enum class AudioSizeClass { SMALLEST, MEDIUM, LARGEST, UNKNOWN }

data class AudioTrackDescription(
    val track: AudioTrack,
    /** Human codec family, for example `Opus` or `AAC`; null when the extractor reported nothing usable. */
    val codecLabel: String?,
    /** Container family, for example `WebM` or `M4A`. */
    val containerLabel: String?,
    val bitrateKbps: Int?,
    /** Best available size in bytes; estimated from bitrate and duration when the extractor gave none. */
    val bytes: Long?,
    val bytesEstimated: Boolean,
    val channels: Int?,
    val sampleRateHz: Int?,
    val dynamicRangeCompressed: Boolean,
    val audioDescription: Boolean,
    val isOriginal: Boolean?,
    val sizeClass: AudioSizeClass,
    /** True for the single rendition the app would pick on its own. */
    val recommended: Boolean,
)

object AudioTracks {
    /** Below this the rendition is a low-quality fallback rather than a normal speech stream. */
    private const val MINIMUM_USEFUL_KBPS = 24

    /**
     * Describes every rendition and returns them in reading order, not in the order the extractor happened
     * to emit. A video dubbed into fifteen languages otherwise buries the recommendation somewhere in the
     * middle of the list, which is where this order comes from.
     */
    fun describe(tracks: List<AudioTrack>, durationMs: Long?): List<AudioTrackDescription> {
        val recommendedId = automatic(tracks)?.id
        val rates = tracks.mapNotNull { effectiveKbps(it) }
        val lowest = rates.minOrNull()
        val highest = rates.maxOrNull()
        return tracks.map { track ->
            val rate = effectiveKbps(track)
            val exact = track.bytes
            val estimated = exact == null && rate != null && durationMs != null && durationMs > 0
            AudioTrackDescription(
                track = track,
                codecLabel = codecLabel(track.codec),
                containerLabel = containerLabel(track.container),
                bitrateKbps = track.bitrateKbps,
                bytes = exact ?: if (estimated) estimateBytes(requireNotNull(rate), requireNotNull(durationMs)) else null,
                bytesEstimated = (track.bytes != null && track.bytesEstimated) || estimated,
                channels = track.channels,
                sampleRateHz = track.sampleRateHz,
                dynamicRangeCompressed = track.dynamicRangeCompressed,
                audioDescription = track.audioDescription,
                isOriginal = track.isOriginal,
                sizeClass = when {
                    rate == null || lowest == null || highest == null || lowest == highest -> AudioSizeClass.UNKNOWN
                    rate <= lowest -> AudioSizeClass.SMALLEST
                    rate >= highest -> AudioSizeClass.LARGEST
                    else -> AudioSizeClass.MEDIUM
                },
                recommended = track.id == recommendedId,
            )
        }.sortedWith(readingOrder)
    }

    /**
     * The order the picker shows. The recommendation first, then the marked original, then the remaining
     * spoken languages, then narration of the picture, which is almost never what someone wants transcribed.
     * Inside one language the same preference decides as for the recommendation, so the plain, smallest
     * usable rendition leads. Languages themselves are ordered by their code rather than by their translated
     * name, so the list reads the same whichever language the app itself is set to.
     */
    private val readingOrder = compareBy<AudioTrackDescription>(
        { if (it.recommended) 0 else 1 },
        { if (it.audioDescription) 1 else 0 },
        { if (it.isOriginal == true) 0 else 1 },
        // A track without a usable language goes last within its group. That is a key of its own rather
        // than a sentinel string, because `language` is carried through from the extractor unchecked and
        // no reserved value can be guaranteed not to appear in it.
        { if (normalizedLanguage(it.track.language) == null) 1 else 0 },
        { normalizedLanguage(it.track.language) ?: "" },
        { if (it.dynamicRangeCompressed) 1 else 0 },
        { if (it.bitrateKbps?.let { rate -> rate < MINIMUM_USEFUL_KBPS } == true) 1 else 0 },
        { it.bitrateKbps ?: Int.MAX_VALUE },
        { it.track.id },
    )

    /**
     * The rendition the app selects without asking. Returns null whenever the choice would silently
     * decide between different spoken languages; a language is a content decision and stays with the user.
     * A language the record refused counts as a language here, not as a missing one — see below.
     */
    fun automatic(tracks: List<AudioTrack>): AudioTrack? {
        if (tracks.isEmpty()) return null
        // An audio-description track narrates the picture; it is never a silent stand-in for the dialogue.
        // When the extractor offers nothing else, transcribing it is a content decision and stays with the user.
        val spoken = tracks.filterNot { it.audioDescription }
        if (spoken.isEmpty()) return null
        val candidates = spoken.filter { it.isOriginal == true }.ifEmpty { spoken }
        // A refused language is not an absent one. Where the source stated a tag the record could not carry,
        // `language` is null and the comparison below reads two such renditions — or one of them beside one
        // that stated nothing — as agreeing, which is exactly the silent decision this function exists to
        // refuse. A single candidate decides nothing between languages and is still chosen.
        if (candidates.size > 1 && candidates.any { it.languageRefused }) return null
        if (candidates.map { normalizedLanguage(it.language) }.distinct().size > 1) return null
        return candidates.sortedWith(preference).firstOrNull()
    }

    /**
     * Smallest usable rendition first, plain before dynamic-range-compressed, then a stable id order.
     * Every key comes from the track record itself, so the same format list always yields the same pick
     * and a later re-resolve cannot silently rebind a running job to a different rendition.
     */
    private val preference = compareBy<AudioTrack>(
        { it.dynamicRangeCompressed },
        { if (effectiveKbps(it)?.let { rate -> rate < MINIMUM_USEFUL_KBPS } == true) 1 else 0 },
        { effectiveKbps(it) ?: Int.MAX_VALUE },
        { it.bytes ?: Long.MAX_VALUE },
        { it.id },
    )

    /** kbit/s is 125 bytes per second; the remainder below a full second is kept instead of truncated away. */
    fun estimateBytes(kbps: Int, durationMs: Long): Long = kbps.toLong() * 125L * durationMs / 1000L

    private fun effectiveKbps(track: AudioTrack): Int? = track.bitrateKbps

    private fun normalizedLanguage(value: String?): String? =
        value?.trim()?.lowercase()?.substringBefore('-')?.takeIf { it.isNotEmpty() }

    private fun codecLabel(codec: String?): String? {
        val value = codec?.trim()?.lowercase() ?: return null
        if (value.isEmpty() || value == "none") return null
        return when {
            value.startsWith("opus") -> "Opus"
            value.startsWith("mp4a") || value.startsWith("aac") -> "AAC"
            value.startsWith("mp3") -> "MP3"
            value.startsWith("vorbis") -> "Vorbis"
            value.startsWith("ec-3") || value.startsWith("eac3") -> "E-AC-3"
            value.startsWith("ac-3") || value.startsWith("ac3") -> "AC-3"
            value.startsWith("flac") -> "FLAC"
            else -> codec.trim().take(20)
        }
    }

    private fun containerLabel(container: String?): String? {
        val value = container?.trim()?.lowercase() ?: return null
        if (value.isEmpty()) return null
        return when (value) {
            "webm" -> "WebM"
            "m4a", "mp4" -> "M4A"
            "mp3" -> "MP3"
            "opus" -> "Opus"
            "ogg" -> "Ogg"
            else -> container.trim().take(10)
        }
    }
}
