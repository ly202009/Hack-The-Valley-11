package com.example.htv11.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.location.Location
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.htv11.R
import com.example.htv11.location.AltitudeFusion
import com.example.htv11.location.BarometerReader
import com.example.htv11.location.FloorClassification
import com.example.htv11.map.DemoLocation
import com.example.htv11.map.ElevationLookupClient
import com.example.htv11.map.MapTilerConfig
import com.example.htv11.map.NightPalette
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val TAG = "GlassBuildingMap"
private const val BUILDING_3D_LAYER_ID = "kaze-building-3d"

private const val LOCATION_UPDATE_INTERVAL_MS = 1500L

private const val GHOST_SOURCE_ID = "kaze-ghost-source"
private const val GHOST_LAYER_ID = "kaze-ghost-layer"
private const val GHOST_ICON_ID = "kaze-ghost-icon"
private const val GHOST_ICON_SIZE_PX = 120
private const val CAUTION_BADGE_COLOR = "#FFC107"
private const val UNDERGROUND_FILL_COLOR = "#4A4F57"
private const val UNDERGROUND_STROKE_COLOR = "#9AA1AA"

@Composable
fun GlassBuildingMapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context).also { it.onCreate(null) } }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocationPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            // Instant snap, not an animated fly-in: this is what judges see on boot.
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(DemoLocation.SAM_IBRAHIM_LAT, DemoLocation.SAM_IBRAHIM_LNG))
                .zoom(DemoLocation.BOOT_ZOOM)
                .tilt(DemoLocation.BOOT_PITCH)
                .build()

            map.setStyle(MapTilerConfig.STYLE_URL) { style ->
                applyNightPalette(style)
                addBuilding3DLayer(style)
                addGhostAvatarLayer(style, context)
                loadedStyle = style
            }
        }
    }

    val barometerReader = remember { BarometerReader(context) }
    val elevationClient = remember { ElevationLookupClient() }
    val altitudeFusion = remember { AltitudeFusion() }

    // The sensor loopback: GPS + barometer + MapTiler ground-truth elevation,
    // fused into a floor classification, pushed straight into the ghost marker.
    LaunchedEffect(loadedStyle, hasLocationPermission) {
        val style = loadedStyle ?: return@LaunchedEffect
        if (!hasLocationPermission) return@LaunchedEffect

        launch {
            barometerReader.pressureReadings().collect { pressure ->
                altitudeFusion.onPressureReading(pressure)
            }
        }

        rawLocationUpdates(context).collect { location ->
            val groundTruth = elevationClient.groundElevationMeters(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null
            )
            val fused = altitudeFusion.onLocationUpdate(
                location = location,
                groundTruthMeters = groundTruth,
                hasBarometerHardware = barometerReader.hasBarometerHardware
            )

            updateGhostAvatar(
                style = style,
                context = context,
                ghostLocation = GhostLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    clampedAltitudeMeters = fused.clampedAltitudeMeters,
                    classification = fused.classification
                )
            )
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView }
    )
}

// Recolors the loaded style to match the KAZE login/signup screens' navy + cyan
// look, keyed on stable OpenMapTiles source-layer names rather than style-specific
// layer ids, so this survives MapTiler renaming/rebuilding the base style.
private fun applyNightPalette(style: Style) {
    style.layers.forEach { layer ->
        when (layer) {
            is BackgroundLayer -> layer.setProperties(
                PropertyFactory.backgroundColor(NightPalette.BACKGROUND)
            )

            is FillLayer -> when (layer.sourceLayer) {
                "water" -> layer.setProperties(PropertyFactory.fillColor(NightPalette.WATER))
                "building" -> layer.setProperties(PropertyFactory.fillColor(NightPalette.BUILDING_2D))
                "landcover", "landuse", "park" -> layer.setProperties(
                    PropertyFactory.fillColor(NightPalette.LANDCOVER)
                )
            }

            is LineLayer -> when (layer.sourceLayer) {
                "transportation" -> layer.setProperties(PropertyFactory.lineColor(NightPalette.ROAD))
                "waterway" -> layer.setProperties(PropertyFactory.lineColor(NightPalette.WATER))
                "boundary" -> layer.setProperties(PropertyFactory.lineColor(NightPalette.BOUNDARY))
            }

            is SymbolLayer -> layer.setProperties(
                PropertyFactory.textColor(NightPalette.TEXT_PRIMARY),
                PropertyFactory.textHaloColor(NightPalette.BACKGROUND),
                PropertyFactory.textHaloWidth(1.2f)
            )

            is FillExtrusionLayer -> layer.setProperties(
                PropertyFactory.fillExtrusionOpacity(0.65f),
                PropertyFactory.fillExtrusionColor(NightPalette.BUILDING_GLASS)
            )
        }
    }
}

// MapTiler's basemap styles (confirmed against their own "Display buildings in 3D"
// doc) never ship a fill-extrusion buildings layer themselves — the "building"
// source-layer is there with real height data, you just have to add the 3D layer
// yourself. We don't hardcode the vector source id (it differs between MapTiler's
// style generations) — we find whichever vector source the loaded style actually
// has and bind to that.
private fun addBuilding3DLayer(style: Style) {
    if (style.getLayer(BUILDING_3D_LAYER_ID) != null) return

    val vectorSourceId = style.sources.filterIsInstance<VectorSource>().firstOrNull()?.id
    if (vectorSourceId == null) {
        Log.w(
            TAG,
            "No vector source in this style — can't add a 3D buildings layer. " +
                "Sources present: ${style.sources.map { it.id }}"
        )
        return
    }

    val labelLayerId = style.layers.firstOrNull { it is SymbolLayer }?.id

    val building3D = FillExtrusionLayer(BUILDING_3D_LAYER_ID, vectorSourceId).apply {
        setSourceLayer("building")
        setFilter(Expression.not(Expression.has("hide_3d")))
        minZoom = 14f
        setProperties(
            PropertyFactory.fillExtrusionColor(NightPalette.BUILDING_GLASS),
            PropertyFactory.fillExtrusionOpacity(0.65f),
            PropertyFactory.fillExtrusionHeight(Expression.get("height"))
        )
    }

    if (labelLayerId != null) {
        style.addLayerBelow(building3D, labelLayerId)
    } else {
        style.addLayer(building3D)
    }

    Log.d(
        TAG,
        "Added '$BUILDING_3D_LAYER_ID' on vector source '$vectorSourceId', " +
            "source-layer 'building', below label layer '$labelLayerId'"
    )
}

// --- Ghost avatar: sensor -> fusion -> Canvas bitmap -> GeoJsonSource -------

private data class GhostLocation(
    val latitude: Double,
    val longitude: Double,
    val clampedAltitudeMeters: Double,
    val classification: FloorClassification
)

// IMPORTANT, verified against this project's actual android-sdk 13.7.1 jar:
// MapLibre Native's SymbolLayer/PropertyFactory on Android has no elevation or
// height-offset property (that's a MapLibre GL JS-only feature, gated behind a
// terrain source — it doesn't exist in this native SDK version). Point.fromLngLat
// below DOES store the Z value on the GeoJSON geometry (correct, ready for a
// future Firebase payload), but MapLibre Android will not use it to move the
// icon up/down on screen. The gray-out / caution-badge treatment below is the
// actual visual communication of floor state here, not vertical position.
@SuppressLint("MissingPermission") // caller gates this behind a granted runtime permission check
private fun rawLocationUpdates(context: Context): Flow<Location> = callbackFlow {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL_MS)
        .setWaitForAccurateLocation(false) // don't delay the first fix waiting for a "better" one
        .build()

    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { trySend(it) }
        }
    }

    client.requestLocationUpdates(request, callback, null)
    awaitClose { client.removeLocationUpdates(callback) }
}

private fun addGhostAvatarLayer(style: Style, context: Context) {
    if (style.getLayer(GHOST_LAYER_ID) != null) return

    if (style.getSource(GHOST_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(GHOST_SOURCE_ID))
    }

    // Seed a default (confident-ground) icon so the layer has something to
    // reference before the first fused reading arrives.
    style.addImage(GHOST_ICON_ID, createGhostAvatarBitmap(context, FloorClassification.CONFIDENT_GROUND))

    style.addLayer(
        SymbolLayer(GHOST_LAYER_ID, GHOST_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(GHOST_ICON_ID),
            // Fixed value, no zoom-stop expression: stays a constant pixel size
            // on screen regardless of zoom level.
            PropertyFactory.iconSize(1f),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        )
    )
}

private fun updateGhostAvatar(style: Style, context: Context, ghostLocation: GhostLocation) {
    val bitmap = createGhostAvatarBitmap(context, ghostLocation.classification)
    style.addImage(GHOST_ICON_ID, bitmap) // addImage on an existing id replaces it in place

    val point = Point.fromLngLat(ghostLocation.longitude, ghostLocation.latitude, ghostLocation.clampedAltitudeMeters)
    val featureCollection = FeatureCollection.fromFeature(Feature.fromGeometry(point))

    // TODO: Wire payload to Firebase later
    (style.getSource(GHOST_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(featureCollection)
}

private fun createGhostAvatarBitmap(context: Context, classification: FloorClassification): Bitmap {
    val bitmap = Bitmap.createBitmap(GHOST_ICON_SIZE_PX, GHOST_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val center = GHOST_ICON_SIZE_PX / 2f
    val avatarRadius = GHOST_ICON_SIZE_PX / 2f - 10f
    val isUnderground = classification == FloorClassification.CONFIDENT_UNDERGROUND

    val fillColor = if (isUnderground) UNDERGROUND_FILL_COLOR else NightPalette.BUILDING_GLASS
    val strokeColor = if (isUnderground) UNDERGROUND_STROKE_COLOR else NightPalette.ACCENT

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor(fillColor)
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor(strokeColor)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    canvas.drawCircle(center, center, avatarRadius, fillPaint)
    canvas.drawCircle(center, center, avatarRadius, strokePaint)

    // Badge only for the uncertain state — confident-ground gets nothing,
    // confident-underground is communicated purely by the gray-out above.
    if (classification == FloorClassification.UNCERTAIN) {
        drawCautionBadge(context, canvas)
    }

    return bitmap
}

private fun drawCautionBadge(context: Context, canvas: Canvas) {
    val badgeRadius = GHOST_ICON_SIZE_PX / 6f
    val badgeCx = GHOST_ICON_SIZE_PX - badgeRadius - 4f
    val badgeCy = badgeRadius + 4f

    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor(CAUTION_BADGE_COLOR)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(badgeCx, badgeCy, badgeRadius, badgePaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor(NightPalette.BACKGROUND)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(badgeCx, badgeCy, badgeRadius, borderPaint)

    val iconHalf = (badgeRadius * 0.6f).toInt()
    ContextCompat.getDrawable(context, R.drawable.ic_ghost_caution)?.apply {
        setBounds(
            (badgeCx - iconHalf).toInt(),
            (badgeCy - iconHalf).toInt(),
            (badgeCx + iconHalf).toInt(),
            (badgeCy + iconHalf).toInt()
        )
        draw(canvas)
    }
}
