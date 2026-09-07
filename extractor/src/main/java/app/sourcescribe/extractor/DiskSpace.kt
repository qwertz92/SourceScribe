package app.sourcescribe.extractor

import android.annotation.SuppressLint
import java.io.File

// UsableSpace: these are conservative stop guards during active writes, not a request
// to allocate/evict caches. Reclaimable bytes are not already free and could include
// another active job's files. Reservations and write-error handling remain mandatory.
@SuppressLint("UsableSpace")
fun physicalFreeBytes(directory: File): Long = directory.usableSpace
