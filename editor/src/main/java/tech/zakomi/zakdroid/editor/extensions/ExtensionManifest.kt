package tech.zakomi.zakdroid.editor.extensions

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** A snippet file an extension contributes for one language (`c`, `cpp` or `python`). */
data class SnippetContribution(val language: String, val path: String)

/**
 * `extension.json` of a declarative extension. An extension is data only: nothing in it is
 * ever executed. Fields follow VS Code's `package.json` where they exist there.
 */
data class ExtensionManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val license: String,
    val snippets: List<SnippetContribution>,
) {
    companion object {
        const val FILE_NAME = "extension.json"
        private val ID = Regex("^[a-z0-9][a-z0-9-]{0,39}(\\.[a-z0-9][a-z0-9-]{0,39}){1,3}$")
        private val VERSION = Regex("^\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?$")
        val LANGUAGES = setOf("c", "cpp", "python")

        /** Parses and validates a manifest. Throws [IllegalArgumentException] with the reason. */
        fun parse(json: String): ExtensionManifest {
            val root = try {
                JsonParser.parseString(json)
            } catch (e: RuntimeException) {
                throw IllegalArgumentException("extension.json is not valid JSON", e)
            }
            require(root.isJsonObject) { "extension.json must be a JSON object" }
            val o = root.asJsonObject
            val id = o.string("id")
            require(ID.matches(id)) { "\"id\" must look like publisher.name (lowercase letters, digits, '-')" }
            val version = o.string("version")
            require(VERSION.matches(version)) { "\"version\" must look like 1.0.0" }
            val name = o.string("name").ifBlank { id }
            val contributes = o.get("contributes")?.takeIf { it.isJsonObject }?.asJsonObject
            val snippets = contributes?.get("snippets")?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty().map { item ->
                require(item.isJsonObject) { "Each snippet contribution must be an object" }
                val language = item.asJsonObject.string("language")
                require(language in LANGUAGES) { "Unknown snippet language \"$language\" (use c, cpp or python)" }
                SnippetContribution(language, checkedPath(item.asJsonObject.string("path")))
            }
            return ExtensionManifest(
                id = id,
                name = name.take(80),
                version = version,
                description = o.string("description").take(300),
                author = o.string("author").take(80),
                license = o.string("license").take(40),
                snippets = snippets,
            )
        }

        /** A relative path inside the extension folder: no `..`, no absolute paths, no backslashes. */
        fun checkedPath(path: String): String {
            val clean = path.removePrefix("./")
            require(clean.isNotBlank()) { "A contribution has no \"path\"" }
            require(!clean.startsWith("/") && '\\' !in clean && ':' !in clean) { "Path \"$path\" must be relative" }
            require(clean.split('/').none { it == ".." || it == "." || it.isEmpty() }) { "Path \"$path\" must stay inside the extension" }
            return clean
        }

        private fun JsonObject.string(key: String): String =
            get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
    }
}
