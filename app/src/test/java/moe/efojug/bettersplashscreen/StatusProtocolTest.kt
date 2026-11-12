package moe.efojug.bettersplashscreen

import moe.efojug.bettersplashscreen.status.StatusProtocol
import org.junit.Assert.*
import org.junit.Test

class StatusProtocolTest {
    @Test fun acceptsOnlyCurrentChallengeFromExpectedSenderAndVersion() {
        assertTrue(StatusProtocol.accepts(1000, 1000, "fresh", "fresh", 3, 3))
        assertFalse(StatusProtocol.accepts(12345, 1000, "fresh", "fresh", 3, 3))
        assertFalse(StatusProtocol.accepts(1000, 1000, "old", "fresh", 3, 3))
        assertFalse(StatusProtocol.accepts(1000, 1000, null, null, 3, 3))
        assertFalse(StatusProtocol.accepts(1000, 1000, "fresh", "fresh", 2, 3))
        assertFalse(StatusProtocol.accepts(-1, -1, "fresh", "fresh", 3, 3))
    }
}
