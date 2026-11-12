package moe.efojug.bettersplashscreen.policy

/** Shell values only. Never pass these to ActivityRecord in system_server. */
enum class ShellWindowType(val value: Int) {
    NONE(0), SPLASH(1), SNAPSHOT(2), SOLID_COLOR(3), LEGACY(4), WINDOWLESS(5);

    companion object {
        fun from(value: Int): ShellWindowType? = entries.singleOrNull { it.value == value }
    }
}

object SplashPolicy {
    fun restoreType(original: Int): Int = when (ShellWindowType.from(original)) {
        ShellWindowType.LEGACY, ShellWindowType.SOLID_COLOR -> ShellWindowType.SPLASH.value
        else -> original
    }

    fun isSplash(type: Int): Boolean = ShellWindowType.from(type) in setOf(
        ShellWindowType.SPLASH, ShellWindowType.SOLID_COLOR, ShellWindowType.LEGACY,
    )

    // uiMode must come from SystemUI's application resources, not the application's theme.
    fun background(uiMode: Int): Int = if (uiMode and 0x30 == 0x20) {
        0xFF000000.toInt()
    } else {
        0xFFFFFFFF.toInt()
    }
}
