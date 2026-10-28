pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "zakdroid-code"

include(":app")
include(":core")
include(":editor")
include(":terminal")
include(":runtime")

// Termux terminal libraries (Apache-2.0), vendored from termux-app v0.118.3.
include(":termux-terminal-emulator")
include(":termux-terminal-view")
project(":termux-terminal-emulator").projectDir = file("third_party/termux/terminal-emulator")
project(":termux-terminal-view").projectDir = file("third_party/termux/terminal-view")
