package tech.zakomi.zakdroid.editor.extensions

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Unpacks and validates an extension archive. Plain java.io, so it is unit-tested on the JVM. */
object ExtensionInstaller {
    const val MAX_FILES = 200
    const val MAX_TOTAL_BYTES = 5L * 1024 * 1024

    private val SAFE_ID = Regex("^[a-z0-9.-]{3,160}$")

    fun isSafeId(id: String) = SAFE_ID.matches(id) && ".." !in id

    /**
     * Installs the extension in [zip] into `[root]/<id>`, replacing an older copy.
     * `extension.json` may be at the top of the archive or inside one top-level folder.
     * Nothing is left behind when validation fails.
     */
    fun install(zip: InputStream, root: File): ExtensionManifest {
        root.mkdirs()
        val staging = File(root, ".staging-${System.nanoTime()}")
        try {
            unpack(zip, staging)
            val base = when {
                File(staging, ExtensionManifest.FILE_NAME).isFile -> staging
                else -> staging.listFiles().orEmpty().singleOrNull { it.isDirectory }
                    ?.takeIf { File(it, ExtensionManifest.FILE_NAME).isFile }
                    ?: throw IllegalArgumentException("The archive has no ${ExtensionManifest.FILE_NAME}")
            }
            val manifest = ExtensionManifest.parse(File(base, ExtensionManifest.FILE_NAME).readText())
            require(isSafeId(manifest.id)) { "Invalid extension id" }
            for (contribution in manifest.snippets) {
                val file = File(base, contribution.path)
                require(file.isFile) { "Missing file: ${contribution.path}" }
                try {
                    SnippetParser.parse(file.readText())
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("${contribution.path}: ${e.message}", e)
                }
            }
            val target = File(root, manifest.id)
            target.deleteRecursively()
            if (!base.renameTo(target)) {
                base.copyRecursively(target, overwrite = true)
            }
            return manifest
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun unpack(zip: InputStream, into: File) {
        into.mkdirs()
        val canonicalRoot = into.canonicalPath + File.separator
        var files = 0
        var total = 0L
        val buffer = ByteArray(16 * 1024)
        ZipInputStream(zip).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                val target = File(into, name)
                require(target.canonicalPath.startsWith(canonicalRoot) || target.canonicalPath + File.separator == canonicalRoot) {
                    "The archive contains an unsafe path: ${entry.name}"
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }
                require(++files <= MAX_FILES) { "The archive has more than $MAX_FILES files" }
                target.parentFile?.mkdirs()
                target.outputStream().use { out ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_TOTAL_BYTES) { "The extension is larger than ${MAX_TOTAL_BYTES / 1024 / 1024} MB" }
                        out.write(buffer, 0, n)
                    }
                }
            }
        }
        require(files > 0) { "The file is not a zip archive, or it is empty" }
    }
}
