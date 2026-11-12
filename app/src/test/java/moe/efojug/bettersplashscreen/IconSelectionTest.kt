package moe.efojug.bettersplashscreen

import moe.efojug.bettersplashscreen.policy.*
import org.junit.Assert.*
import org.junit.Test

class IconSelectionTest {
    @Test fun verifiedThemeHasPriorityAndStopsFallbacks() {
        val result = selectIcon({ "theme" }, { error("must not run") }, { error("must not run") },
            { error("must not run") }, { _, _ -> fail() })
        assertEquals(IconChoice("theme", IconSource.VERIFIED_COLOROS_THEME), result)
    }
    @Test fun failedThemeAndLauncherFallBackToApplication() {
        val failures = mutableListOf<IconSource>()
        val result = selectIcon({ throw IllegalStateException() }, { throw SecurityException() },
            { "application" }, { "default" }, { source, _ -> failures.add(source) })
        assertEquals(IconSource.APPLICATION, result.source)
        assertEquals(listOf(IconSource.VERIFIED_COLOROS_THEME, IconSource.LAUNCHER_COMPONENT), failures)
    }
    @Test fun launcherBeforeApplicationAndDefaultWhenEverythingIsMissing() {
        assertEquals(IconSource.LAUNCHER_COMPONENT,
            selectIcon({ null }, { "launcher" }, { "app" }, { "default" }, { _, _ -> }).source)
        assertEquals(IconChoice("default", IconSource.SYSTEM_DEFAULT),
            selectIcon({ null }, { null }, { null }, { "default" }, { _, _ -> }))
    }
    @Test fun signatureMatchAloneNeverQualifiesAsThemeEvidence() {
        assertNull(ThemeEvidence().verified("unverified-firmware", "oplus-drawable"))
        val evidence = ThemeEvidence(setOf("validated-build"))
        assertNull(evidence.verified("different-build", "drawable"))
        assertEquals("drawable", evidence.verified("validated-build", "drawable"))
        assertNull(evidence.verified<String>("validated-build", null))
    }
}
