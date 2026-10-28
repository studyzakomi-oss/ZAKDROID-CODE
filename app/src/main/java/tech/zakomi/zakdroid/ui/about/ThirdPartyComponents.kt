package tech.zakomi.zakdroid.ui.about

import androidx.annotation.StringRes
import tech.zakomi.zakdroid.R

/** Every third-party component in Phase 1. Keep in sync with THIRD_PARTY_LICENSES.md. */
data class ThirdPartyComponent(val name: String, val license: String, @StringRes val purpose: Int)

val ThirdPartyComponents = listOf(
    ThirdPartyComponent("sora-editor 0.24.7 (editor, language-textmate)", "LGPL-2.1", R.string.purpose_sora),
    ThirdPartyComponent("Eclipse tm4e (in language-textmate)", "EPL-2.0", R.string.purpose_tm4e),
    ThirdPartyComponent("Eclipse JDT annotations", "EPL-2.0", R.string.purpose_tm4e),
    ThirdPartyComponent("Joni 2.2.7, JCodings 1.0.64", "MIT", R.string.purpose_regex),
    ThirdPartyComponent("Gson 2.14.0, SnakeYAML Engine 3.2", "Apache-2.0", R.string.purpose_json),
    ThirdPartyComponent("VS Code grammars & language configs (better-c-syntax, better-cpp-syntax, MagicPython)", "MIT", R.string.purpose_grammars),
    ThirdPartyComponent("Termux terminal-emulator & terminal-view 0.118.3", "Apache-2.0", R.string.purpose_terminal),
    ThirdPartyComponent("PRoot 5.1.107.101 (Termux fork)", "GPL-2.0-or-later", R.string.purpose_proot),
    ThirdPartyComponent("talloc 2.5.0", "LGPL-3.0-or-later", R.string.purpose_talloc),
    ThirdPartyComponent("Alpine Linux 3.24.3 + packages (musl, BusyBox, clang/LLVM, GCC, Python…)", "MIT, GPL-2.0, Apache-2.0 WITH LLVM-exception, GPL-3.0, PSF-2.0 and others", R.string.purpose_alpine),
    ThirdPartyComponent("Apache Commons Compress 1.28.0", "Apache-2.0", R.string.purpose_compress),
    ThirdPartyComponent("OkHttp 5.5.1, Okio 3.17.0", "Apache-2.0", R.string.purpose_okhttp),
    ThirdPartyComponent("AndroidX: Compose, Material 3, Activity, Lifecycle, Room, DataStore, WorkManager, SplashScreen, DocumentFile, Hilt-Work", "Apache-2.0", R.string.purpose_androidx),
    ThirdPartyComponent("Material Icons (Compose)", "Apache-2.0", R.string.purpose_icons),
    ThirdPartyComponent("Dagger Hilt 2.60.1", "Apache-2.0", R.string.purpose_hilt),
    ThirdPartyComponent("Kotlin 2.4.21, kotlinx.coroutines 1.11.0", "Apache-2.0", R.string.purpose_kotlin),
    ThirdPartyComponent("Montserrat", "OFL-1.1", R.string.purpose_ui_font),
    ThirdPartyComponent("JetBrains Mono", "OFL-1.1", R.string.purpose_code_font),
)
