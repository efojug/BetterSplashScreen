package moe.efojug.bettersplashscreen.policy

enum class IconSource { VERIFIED_COLOROS_THEME, LAUNCHER_COMPONENT, APPLICATION, SYSTEM_DEFAULT }

data class IconChoice<T : Any>(val icon: T, val source: IconSource)

/** Failing one optional source must not prevent a later source from being tried. */
fun <T : Any> selectIcon(
    theme: () -> T?,
    launcher: () -> T?,
    application: () -> T?,
    default: () -> T,
    onFailure: (IconSource, Exception) -> Unit,
): IconChoice<T> {
    val sources = listOf(
        IconSource.VERIFIED_COLOROS_THEME to theme,
        IconSource.LAUNCHER_COMPONENT to launcher,
        IconSource.APPLICATION to application,
    )
    for ((source, load) in sources) {
        val icon = try {
            load()
        } catch (e: Exception) {
            onFailure(source, e); null
        }
        if (icon != null) return IconChoice(icon, source)
    }
    return IconChoice(default(), IconSource.SYSTEM_DEFAULT)
}

/** A matched method signature is not evidence that its Drawable includes the desktop theme. */
class ThemeEvidence(private val verifiedFingerprints: Set<String> = emptySet()) {
    fun <T : Any> verified(fingerprint: String, candidate: T?): T? =
        candidate?.takeIf { fingerprint in verifiedFingerprints }
}
