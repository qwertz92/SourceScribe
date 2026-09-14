package app.sourcescribe.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The installed app build and the engine it bundles, as the start-up check of ADR 0012 tells them apart: the package's
 * version code, the time the package was last installed or updated, and the SHA-256 of the bundled engine resource.
 */
@Serializable
data class BundledEngineIdentity(
    val versionCode: Long,
    val lastUpdateTime: Long,
    val resourceSha256: String,
)

/** The note in app-private storage that the full check of the bundled engine passed for one [BundledEngineIdentity]. */
object BundledEngineMarker {
    fun encode(identity: BundledEngineIdentity): String = Json.encodeToString(BundledEngineIdentity.serializer(), identity)

    /**
     * Whether a start has to run the full check: always, unless [stored] is the marker written for exactly [current]. A
     * missing, damaged or unknown marker skips nothing.
     */
    fun fullCheckNeeded(stored: String?, current: BundledEngineIdentity): Boolean {
        if (stored == null) return true
        val recorded = try {
            Json.decodeFromString(BundledEngineIdentity.serializer(), stored)
        } catch (_: IllegalArgumentException) {
            // kotlinx.serialization reports malformed text and missing or unknown fields as subclasses of this.
            return true
        }
        return recorded != current
    }
}
