package com.example.htv11.map

import com.example.htv11.BuildConfig

object MapTilerConfig {
    val API_KEY: String = BuildConfig.MAPTILER_API_KEY

    // "streets" family keeps full street/building detail (needed for the
    // fill-extrusion glass-building effect) while still giving us a dark base.
    // Fallbacks if this one looks off once you test with a real key:
    //   "basic-v2-dark"   -> flatter, fewer POIs, still has buildings
    //   "streets-v2-dark" -> same family, standard dark instead of "night"
    val STYLE_URL = "https://api.maptiler.com/maps/streets-v2-night/style.json?key=$API_KEY"
}
