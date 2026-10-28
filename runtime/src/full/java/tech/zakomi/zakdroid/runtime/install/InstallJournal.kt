package tech.zakomi.zakdroid.runtime.install

import tech.zakomi.zakdroid.runtime.InstallStep
import java.io.File
import java.util.Properties

/** Persists completed install steps so a cancelled or failed install resumes where it stopped. */
class InstallJournal(private val file: File) {
    private val props = Properties().apply {
        if (file.exists()) file.inputStream().use(::load)
    }

    fun isDone(step: InstallStep): Boolean =
        props.getProperty(step.name) == AlpineRelease.VERSION

    fun markDone(step: InstallStep) {
        props.setProperty(step.name, AlpineRelease.VERSION)
        save()
    }

    /** Forgets [step] and every step after it. */
    fun invalidateFrom(step: InstallStep) {
        InstallStep.entries.filter { it.ordinal >= step.ordinal }.forEach { props.remove(it.name) }
        save()
    }

    fun hasProgress(): Boolean = props.isNotEmpty()

    fun clear() {
        props.clear()
        file.delete()
    }

    private fun save() {
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, "zakdroid-code runtime install journal") }
    }
}
