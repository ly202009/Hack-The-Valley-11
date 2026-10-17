package com.example.htv11.ui

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.htv11.map.DemoLocation
import com.example.htv11.map.MapTilerConfig
import com.example.htv11.map.NightPalette
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
import org.maplibre.android.style.sources.VectorSource

private const val TAG = "GlassBuildingMap"
private const val BUILDING_3D_LAYER_ID = "kaze-building-3d"

@Composable
fun GlassBuildingMapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context).also { it.onCreate(null) } }

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
            }
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
