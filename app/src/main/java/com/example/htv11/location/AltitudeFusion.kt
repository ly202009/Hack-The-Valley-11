package com.example.htv11.location

import android.location.Location

private const val STARTUP_BURST_WINDOW_MS = 12_000L // burst-average first ~10-15s rather than trust one fix
private const val GOOD_VERTICAL_ACCURACY_METERS = 10f
private const val MEDIUM_VERTICAL_ACCURACY_METERS = 30f
private const val GOOD_TIER_BAROMETER_WEIGHT = 0.9
private const val MEDIUM_TIER_BAROMETER_WEIGHT = 0.7

enum class FloorClassification { CONFIDENT_GROUND, CONFIDENT_UNDERGROUND, UNCERTAIN }

data class FusedAltitude(
    val classification: FloorClassification,
    val clampedAltitudeMeters: Double
)

private enum class AccuracyTier { GOOD, MEDIUM, BAD_OR_UNKNOWN }

/**
 * Complementary-filter altitude fusion: GPS + MapTiler ground-truth elevation +
 * barometer (when present), blended with a fixed/tiered ratio rather than
 * statistically-optimal (inverse-variance) weighting. The MVP version was
 * chosen deliberately: the three things that actually make "same floor -> same
 * reading" true — a shared ground reference, the barometer's raw physical
 * precision, and the 4m floor-snap bucket — are identical in both approaches.
 * The full version's main edge (graceful handling of fluctuating uncertainty)
 * requires inventing an unvalidated barometer-drift-uncertainty model (Android
 * doesn't report one), which is more likely to introduce inconsistency than
 * prevent it under hackathon time pressure.
 *
 * Stateful — one instance per tracking session, not a pure function (it holds
 * the barometer's anchor point and pressure baseline between calls).
 */
class AltitudeFusion {
    private var latestPressureHpa: Float? = null

    private var heightAboveGroundAnchor: Double? = null
    private var pressureAtAnchorHpa: Float? = null

    private var startupStartTimeMillis: Long? = null
    private var hasCompletedStartup = false
    private val startupHeightSamples = mutableListOf<Double>()

    fun onPressureReading(pressureHpa: Float) {
        latestPressureHpa = pressureHpa
    }

    fun onLocationUpdate(
        location: Location,
        groundTruthMeters: Double?,
        hasBarometerHardware: Boolean
    ): FusedAltitude {
        if (groundTruthMeters == null) {
            // No ground reference yet (elevation lookup still in flight, or it
            // failed with nothing cached) — nothing to compare against.
            return FusedAltitude(FloorClassification.UNCERTAIN, clampedAltitudeMeters = 0.0)
        }

        val gpsHeightAboveGround = if (location.hasAltitude()) {
            location.altitude - groundTruthMeters
        } else {
            null
        }

        if (!hasCompletedStartup) {
            val now = System.currentTimeMillis()
            val startedAt = startupStartTimeMillis ?: now.also { startupStartTimeMillis = it }
            gpsHeightAboveGround?.let { startupHeightSamples.add(it) }

            if (now - startedAt < STARTUP_BURST_WINDOW_MS) {
                return FusedAltitude(FloorClassification.UNCERTAIN, clampedAltitudeMeters = groundTruthMeters)
            }

            hasCompletedStartup = true
            val initialHeight = if (startupHeightSamples.isNotEmpty()) {
                startupHeightSamples.average()
            } else {
                gpsHeightAboveGround ?: 0.0
            }
            heightAboveGroundAnchor = initialHeight
            pressureAtAnchorHpa = latestPressureHpa
            // Fall through: this tick already has a fresh anchor, use it below.
        }

        val verticalAccuracy = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else null
        val tier = classifyTier(verticalAccuracy)
        val barometerTrackedHeight = barometerTrackedHeightOrNull(hasBarometerHardware)

        val fusedHeightAboveGround: Double? = when {
            tier == AccuracyTier.GOOD && barometerTrackedHeight != null && gpsHeightAboveGround != null -> {
                val blended = GOOD_TIER_BAROMETER_WEIGHT * barometerTrackedHeight +
                    (1 - GOOD_TIER_BAROMETER_WEIGHT) * gpsHeightAboveGround
                // Drift correction: nudge the anchor toward GPS, don't hard-reset to it.
                reanchor(blended)
                blended
            }

            tier == AccuracyTier.MEDIUM && barometerTrackedHeight != null && gpsHeightAboveGround != null -> {
                // Deliberately not re-anchoring here — only "good" fixes correct
                // barometer drift, per the fixed/tiered design.
                MEDIUM_TIER_BAROMETER_WEIGHT * barometerTrackedHeight +
                    (1 - MEDIUM_TIER_BAROMETER_WEIGHT) * gpsHeightAboveGround
            }

            else -> gpsHeightAboveGround // bad accuracy, or no barometer: GPS/ground-truth only
        }

        if (fusedHeightAboveGround == null) {
            return FusedAltitude(FloorClassification.UNCERTAIN, clampedAltitudeMeters = groundTruthMeters)
        }

        val snappedHeight = snapToFloorHeight(fusedHeightAboveGround)
        val isUnderground = snappedHeight < 0.0
        val trusted = tier != AccuracyTier.BAD_OR_UNKNOWN

        val classification = when {
            !trusted -> FloorClassification.UNCERTAIN
            isUnderground -> FloorClassification.CONFIDENT_UNDERGROUND
            else -> FloorClassification.CONFIDENT_GROUND
        }

        // Clamp underground readings back to the ground plane for rendering.
        val clampedAltitude = if (isUnderground) groundTruthMeters else groundTruthMeters + snappedHeight

        return FusedAltitude(classification, clampedAltitude)
    }

    private fun barometerTrackedHeightOrNull(hasBarometerHardware: Boolean): Double? {
        val anchor = heightAboveGroundAnchor
        val anchorPressure = pressureAtAnchorHpa
        val currentPressure = latestPressureHpa
        if (!hasBarometerHardware || anchor == null || anchorPressure == null || currentPressure == null) {
            return null
        }
        return anchor + barometricDeltaMeters(anchorPressure, currentPressure)
    }

    private fun reanchor(newHeightAboveGround: Double) {
        heightAboveGroundAnchor = newHeightAboveGround
        pressureAtAnchorHpa = latestPressureHpa
    }

    private fun classifyTier(verticalAccuracyMeters: Float?): AccuracyTier = when {
        verticalAccuracyMeters == null -> AccuracyTier.BAD_OR_UNKNOWN
        verticalAccuracyMeters < GOOD_VERTICAL_ACCURACY_METERS -> AccuracyTier.GOOD
        verticalAccuracyMeters < MEDIUM_VERTICAL_ACCURACY_METERS -> AccuracyTier.MEDIUM
        else -> AccuracyTier.BAD_OR_UNKNOWN
    }

    private fun barometricDeltaMeters(referenceHpa: Float, currentHpa: Float): Double {
        // Standard barometric formula, referenced to our own local baseline
        // pressure rather than true sea-level pressure — accurate for the
        // relative/short-term changes we actually need, not absolute altitude.
        return 44330.0 * (1.0 - Math.pow((currentHpa / referenceHpa).toDouble(), 1.0 / 5.255))
    }
}
