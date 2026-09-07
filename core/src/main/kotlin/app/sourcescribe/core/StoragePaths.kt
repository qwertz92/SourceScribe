package app.sourcescribe.core

import java.nio.file.Files
import java.nio.file.Path

/** Android owns these aliases; app-created links below them remain untrusted. */
fun isUntrustedStorageSymlink(path: Path): Boolean {
    if (!Files.isSymbolicLink(path)) return false
    val value = path.toAbsolutePath().normalize().toString()
    val android = System.getProperty("java.vm.name") == "Dalvik"
    return !(android && (value == "/data/data" || value.matches(Regex("/data/user(?:_de)?/[0-9]+"))))
}
