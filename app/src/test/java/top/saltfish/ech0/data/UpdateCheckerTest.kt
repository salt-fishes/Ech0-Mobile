package top.saltfish.ech0.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `compares numeric versions`() {
        assertTrue(UpdateChecker.isVersionNewer("0.5.3", "0.5.2"))
        assertTrue(UpdateChecker.isVersionNewer("0.6", "0.5.9"))
        assertFalse(UpdateChecker.isVersionNewer("0.5.3", "0.5.3"))
        assertFalse(UpdateChecker.isVersionNewer("0.5.2", "0.5.3"))
    }

    @Test
    fun `ignores prerelease suffix`() {
        assertFalse(UpdateChecker.isVersionNewer("0.5.3-beta", "0.5.3"))
    }
}
