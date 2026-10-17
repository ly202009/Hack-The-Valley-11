package com.example.htv11.location

private const val FLOOR_HEIGHT_METERS = 4.0

// Buckets a raw height reading into 4m "floor" steps so two people on the same
// floor read identically despite GPS/barometer noise. Known limitation: a
// reading right at a bucket boundary (e.g. 1.9m vs 2.1m) can still snap to
// different floors despite being practically the same height — 4m gives good
// real-world margin for typical floor-to-floor height, but it's not bulletproof
// at the edges.
fun snapToFloorHeight(rawMeters: Double): Double =
    Math.round(rawMeters / FLOOR_HEIGHT_METERS) * FLOOR_HEIGHT_METERS
