package app.sourcescribe.data

import kotlinx.coroutines.sync.Mutex

/** Serializes source file commits with job creation and deletion in the single app process. */
internal object SourceFiles {
    // ponytail: one lock for at most four jobs; per-source locks only if contention becomes measurable.
    val mutex = Mutex()
    private val previewOwners = mutableSetOf<String>()
    private val previews = mutableMapOf<String, MutableSet<String>>()

    @Synchronized fun registerPreviewOwner(owner: String) { previewOwners += owner }
    @Synchronized fun reservePreview(sourceId: String, owner: String) {
        if (owner in previewOwners) previews.getOrPut(sourceId) { mutableSetOf() }.add(owner)
    }
    @Synchronized fun hasPreview(sourceId: String): Boolean = previews[sourceId]?.isNotEmpty() == true
    @Synchronized fun releasePreview(sourceId: String, owner: String) {
        previews[sourceId]?.remove(owner)
        if (previews[sourceId]?.isEmpty() == true) previews.remove(sourceId)
    }
    @Synchronized fun unregisterPreviewOwner(owner: String) {
        previewOwners -= owner
        previews.values.forEach { it.remove(owner) }
        previews.entries.removeAll { it.value.isEmpty() }
    }
}
