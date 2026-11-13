package moe.efojug.bettersplashscreen

import moe.efojug.bettersplashscreen.policy.SplashPolicy
import org.junit.Assert.*
import org.junit.Test

class SplashPolicyTest {
    @Test fun nativeAppearancePreservesLegacyRemovalOwnership() {
        assertEquals(1, SplashPolicy.restoreType(4))
        assertTrue(SplashPolicy.preventAppHandoff(4))
        listOf(-1, 0, 1, 2, 3, 5, 99).forEach {
            assertFalse(SplashPolicy.preventAppHandoff(it))
        }
    }
    @Test fun restoresOnlyExistingSplashVariants() {
        assertEquals(1, SplashPolicy.restoreType(3))
        assertEquals(1, SplashPolicy.restoreType(4))
        listOf(0, 1, 2, 5, -1, 99).forEach { assertEquals(it, SplashPolicy.restoreType(it)) }
    }
    @Test fun restoringIsIdempotentAndNeverConvertsSnapshot() {
        (-1..6).forEach {
            assertEquals(SplashPolicy.restoreType(it), SplashPolicy.restoreType(SplashPolicy.restoreType(it)))
        }
        listOf(0, 2, 5).forEach { assertFalse(SplashPolicy.isSplash(it)) }
    }
    @Test fun backgroundUsesOnlyNightMaskAndIsOpaque() {
        assertEquals(0xFFFFFFFF.toInt(), SplashPolicy.background(0x11))
        assertEquals(0xFF000000.toInt(), SplashPolicy.background(0x21))
        assertEquals(0xFF000000.toInt(), SplashPolicy.background(0x26))
        assertEquals(0xFFFFFFFF.toInt(), SplashPolicy.background(0))
        assertEquals(255, SplashPolicy.background(0x20).ushr(24))
    }
}
