package com.example.htv11

import org.junit.Assert.*
import org.junit.Test

class VolumeDuressDetectorTest {

    @Test
    fun testValidUpDownPattern_singlePressEach() {
        var triggered = false
        val detector = VolumeDuressDetector(resetTimeoutMillis = 2000L) {
            triggered = true
        }

        val up = VolumeDuressDetector.VolumePress.UP
        val down = VolumeDuressDetector.VolumePress.DOWN

        // Sequence: Up, Down, Up, Down, Up, Down (1 time each, 3 repetitions)
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(up))
        assertTrue(detector.onVolumePress(down)) // Should trigger on completion

        assertTrue("Duress should be triggered for valid up-down pattern", triggered)
    }

    @Test
    fun testValidDownUpPattern_doublePressEach() {
        var triggered = false
        val detector = VolumeDuressDetector(resetTimeoutMillis = 2000L) {
            triggered = true
        }

        val up = VolumeDuressDetector.VolumePress.UP
        val down = VolumeDuressDetector.VolumePress.DOWN

        // Sequence: Down, Down, Up, Up, Down, Down, Up, Up, Down, Down, Up, Up (2 times each, 3 repetitions)
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(up))
        assertTrue(detector.onVolumePress(up))

        assertTrue("Duress should be triggered for valid down-up double-press pattern", triggered)
    }

    @Test
    fun testThreeInARowResetsCount() {
        var triggered = false
        val detector = VolumeDuressDetector(resetTimeoutMillis = 2000L) {
            triggered = true
        }

        val up = VolumeDuressDetector.VolumePress.UP
        val down = VolumeDuressDetector.VolumePress.DOWN

        // Up, Down, Up (valid start)
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(up))

        // 3 ups in a row -> should reset counts
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(up))

        // Send an incomplete/invalid sequence after reset (e.g. only 2 presses)
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(up))

        assertFalse("Duress should NOT trigger because 3 in a row reset the state and sequence was incomplete", triggered)
    }
}
