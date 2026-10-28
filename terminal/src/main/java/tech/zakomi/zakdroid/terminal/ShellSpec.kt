package tech.zakomi.zakdroid.terminal

/**
 * A process to run in a terminal tab.
 *
 * @param argv full argument vector; argv[0] is the process name.
 * @param env environment as KEY=VALUE strings; nothing is inherited from the app.
 */
data class ShellSpec(
    val title: String,
    val executable: String,
    val argv: List<String>,
    val cwd: String,
    val env: List<String>,
    val kind: Kind = Kind.Shell,
) {
    enum class Kind { Shell, Run }
}
