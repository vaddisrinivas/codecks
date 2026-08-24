package io.codecks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidRestrictedMediaPolicyTest {
    @Test
    fun restrictedMediaAllowlistIsExact() {
        assertTrue(HidCommand.MediaPlayPause.isRestrictedMediaCommand())
        assertTrue(HidCommand.MediaMute.isRestrictedMediaCommand())
        assertTrue(HidCommand.MediaVolumeDown.isRestrictedMediaCommand())
        assertTrue(HidCommand.MediaVolumeUp.isRestrictedMediaCommand())
        assertFalse(HidCommand.MediaNext.isRestrictedMediaCommand())
        assertFalse(HidCommand.Copy.isRestrictedMediaCommand())
        assertFalse(HidCommand.SpaceRight.isRestrictedMediaCommand())
    }
}
