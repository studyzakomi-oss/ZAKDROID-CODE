package tech.zakomi.zakdroid.runtime.install

import tech.zakomi.zakdroid.runtime.RuntimeErrorKind

class InstallException(val kind: RuntimeErrorKind, message: String, cause: Throwable? = null) : Exception(message, cause)
