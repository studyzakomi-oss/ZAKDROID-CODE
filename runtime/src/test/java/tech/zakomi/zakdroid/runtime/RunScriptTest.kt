package tech.zakomi.zakdroid.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunScriptTest {

    private fun target(language: RunLanguage, name: String) =
        RunTarget(language, name, "/home/user/projects/demo/$name", "/home/user/projects/demo")

    @Test
    fun `C build uses clang with debug and warning flags and no sanitizer`() {
        val script = RunScript.build(target(RunLanguage.C, "main.c"))
        assertTrue(script.contains("clang -g -Wall -Wextra -std=c17 -o "))
        assertTrue(script.contains("'/home/user/projects/demo/main.c' -lm"))
        assertFalse(script.contains("sanitize"))
    }

    @Test
    fun `C++ build uses clang++ with C++20`() {
        val script = RunScript.build(target(RunLanguage.Cpp, "main.cpp"))
        assertTrue(script.contains("clang++ -g -Wall -Wextra -std=c++20 -o "))
        assertFalse(script.contains("sanitize"))
    }

    @Test
    fun `Python runs unbuffered without a build step`() {
        val script = RunScript.build(target(RunLanguage.Python, "main.py"))
        assertTrue(script.contains("python3 -u '/home/user/projects/demo/main.py'"))
        assertFalse(script.contains("clang"))
    }

    @Test
    fun `every script prints exit code and execution time`() {
        RunLanguage.entries.forEach { language ->
            val script = RunScript.build(target(language, "x.${language.name.lowercase()}"))
            assertTrue(script.contains("exited with code %d · %s s"))
            assertTrue(script.contains("footer \"\$status\""))
        }
    }

    @Test
    fun `quote escapes single quotes for sh`() {
        assertEquals("'it'\\''s here'", RunScript.quote("it's here"))
        assertEquals("'a b'", RunScript.quote("a b"))
    }
}
