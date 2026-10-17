package com.example.htv11

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VolumeDuressManager {
    private val _detectorState = MutableStateFlow(VolumeDuressDetector.DetectorState())
    val detectorState: StateFlow<VolumeDuressDetector.DetectorState> = _detectorState.asStateFlow()

    var resetTimeoutMillis: Long = 2000L
        set(value) {
            field = value
            detector.resetTimeoutMillis = value
        }

    var onDuressTriggered: (() -> Unit)? = null

    val detector = VolumeDuressDetector(
        resetTimeoutMillis = resetTimeoutMillis,
        onStateChanged = { state ->
            _detectorState.value = state
        },
        onDuressDetected = {
            onDuressTriggered?.invoke()
        }
    )

    fun onVolumePress(press: VolumeDuressDetector.VolumePress): Boolean {
        return detector.onVolumePress(press)
    }

    fun reset() {
        detector.resetState()
    }
}
