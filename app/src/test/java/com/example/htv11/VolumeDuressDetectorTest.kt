package com.example.htv11

import org.junit.Assert.*
import org.junit.Test

class VolumeDuressDetectorTest {

    @Test
    fun testValidUpDownPattern_singlePressEach() {
        var triggered = false
        var currentTime = 1000L
        val detector = VolumeDuressDetector(
            resetTimeoutMillis = 2000L,
            timeProvider = { currentTime += 100L; currentTime }
        ) {
            triggered = true
        }

        val up = VolumeDuressDetector.VolumePress.UP
        val down = VolumeDuressDetector.VolumePress.DOWN

        // Sequence: Up, Down, Up, Down, Up, Down (both counts reach 3)
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(up))
        assertTrue(detector.onVolumePress(down)) // Triggered on 6th press

        assertTrue("Duress should be triggered when upCount >= 3 and downCount >= 3", triggered)
    }

    @Test
    fun testValidDownUpPattern_doublePressEach() {
        var triggered = false
        var currentTime = 1000L
        val detector = VolumeDuressDetector(
            resetTimeoutMillis = 2000L,
            timeProvider = { currentTime += 100L; currentTime }
        ) {
            triggered = true
        }

        val up = VolumeDuressDetector.VolumePress.UP
        val down = VolumeDuressDetector.VolumePress.DOWN

        // Sequence: Down, Down, Up, Up, Down, Up
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(down))
        assertTrue(detector.onVolumePress(up))

        assertTrue("Duress should be triggered once both upCount >= 3 and downCount >= 3", triggered)
    }

    @Test
    fun testThreeInARowResetsCount() {
        var triggered = false
        var currentTime = 1000L
        val detector = VolumeDuressDetector(
            resetTimeoutMillis = 2000L,
            timeProvider = { currentTime += 100L; currentTime }
        ) {
            triggered = true
        }

        val up = VolumeDuressDetector.VolumePress.UP
        val down = VolumeDuressDetector.VolumePress.DOWN

        // Up, Down, Up (up=2, down=1)
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(up))

        // 3 ups in a row -> should reset counts
        assertFalse(detector.onVolumePress(up))
        assertFalse(detector.onVolumePress(up))

        // Send incomplete sequence after reset
        assertFalse(detector.onVolumePress(down))
        assertFalse(detector.onVolumePress(up))

        assertFalse("Duress should NOT trigger because 3 in a row reset the state and sequence was incomplete", triggered)
    }
}
