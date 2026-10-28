package tech.zakomi.zakdroid.editor

import tech.zakomi.zakdroid.core.fs.FileNames

/** Languages with syntax highlighting in Phase 1, keyed by TextMate scope name. */
enum class EditorLanguage(val displayName: String, val scopeName: String?) {
    C("C", "source.c"),
    Cpp("C++", "source.cpp"),
    Python("Python", "source.python"),
    PlainText("Plain Text", null);

    companion object {
        private val C_EXTENSIONS = setOf("c", "i")
        private val CPP_EXTENSIONS = setOf(
            "cpp", "cc", "cxx", "c++", "cppm", "ccm", "cxxm", "hpp", "hh", "hxx", "h++", "h",
            "ii", "ino", "inl", "ipp", "ixx", "tpp", "txx",
        )
        private val PYTHON_EXTENSIONS = setOf("py", "pyw", "pyi")

        /** Mirrors VS Code's file associations: `.h` is treated as C++. */
        fun fromFileName(name: String): EditorLanguage {
            val ext = FileNames.extension(name)
            return when {
                ext in C_EXTENSIONS -> C
                ext in CPP_EXTENSIONS -> Cpp
                ext in PYTHON_EXTENSIONS -> Python
                else -> PlainText
            }
        }
    }
}
