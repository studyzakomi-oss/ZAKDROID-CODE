package tech.zakomi.zakdroid.ui.runtime

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import tech.zakomi.zakdroid.runtime.RuntimeManager
import tech.zakomi.zakdroid.runtime.RuntimeState
import javax.inject.Inject

@HiltViewModel
class RuntimeViewModel @Inject constructor(private val runtime: RuntimeManager) : ViewModel() {
    val state: StateFlow<RuntimeState> = runtime.state
    val description: String get() = runtime.description
    val packages: List<String> get() = runtime.packages

    fun install() = runtime.install()
    fun cancel() = runtime.cancel()
    fun repair() = runtime.repair()
    fun reinstall() = runtime.reinstall()
}
