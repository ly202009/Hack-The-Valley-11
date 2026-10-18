package com.example.htv11.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.example.htv11.AuthManager
import com.example.htv11.DynamicBlueGridBackground
import com.example.htv11.GroupLocation
import com.example.htv11.GroupMember
import com.example.htv11.R
import com.example.htv11.UserRepository
import com.example.htv11.location.FloorClassification
import com.example.htv11.map.DemoLocation
import com.example.htv11.map.MapTilerConfig
import com.example.htv11.map.NightPalette
import com.google.gson.JsonObject
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

private const val GHOST_SOURCE_ID = "kaze-ghost-source"
private const val GHOST_LAYER_ID = "kaze-ghost-layer"
private const val GHOST_ICON_GROUND_ID = "kaze-ghost-ground"
private const val GHOST_ICON_UNDERGROUND_ID = "kaze-ghost-underground"
private const val GHOST_ICON_UNCERTAIN_ID = "kaze-ghost-uncertain"
private const val GHOST_ICON_SIZE_PX = 120
private const val CAUTION_BADGE_COLOR = "#FFC107"
private const val UNDERGROUND_FILL_COLOR = "#4A4F57"
private const val UNDERGROUND_STROKE_COLOR = "#9AA1AA"
// Same red MemberCard already uses for status == "danger" in the group list.
private const val UNSAFE_GLOW_COLOR = "#FF5252"

// Pure renderer: the GPS/barometer/altitude-fusion pipeline that produces this
// data lives in MainActivity now (runs the whole time you're signed in,
// regardless of which tab is selected) — this screen just reads the shared
// Firebase state and draws it, same as it already did for everyone else's
// markers, now including its own. groupLocations is likewise owned and kept
// warm by MainActivity (not fetched independently here), so by the time this
// screen is entered, your own synced position is usually already on hand.
@Composable
fun GlassBuildingMapScreen(
    groupId: String = "DEFAULT_SQUAD",
    groupLocations: List<GroupLocation>,
    hasLocationPermission: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context).also { it.onCreate(null) } }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }

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

    // One-shot camera target for this visit to the screen: either my own synced position, or —
    // only if permission was never granted, so nothing is ever coming — the Sam Ibrahim fallback.
    // Resolves exactly once and locks, so there's never a visible jump from one to the other; the
    // map simply isn't shown (see the Box below) until this resolves.
    val myLocation = groupLocations.firstOrNull { it.userId == AuthManager.currentUser?.uid }
    var cameraTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var cameraResolved by remember { mutableStateOf(false) }

    LaunchedEffect(myLocation, hasLocationPermission) {
        if (cameraResolved) return@LaunchedEffect
        when {
            myLocation != null -> {
                cameraTarget = myLocation.latitude to myLocation.longitude
                cameraResolved = true
            }
            !hasLocationPermission -> {
                cameraTarget = DemoLocation.SAM_IBRAHIM_LAT to DemoLocation.SAM_IBRAHIM_LNG
                cameraResolved = true
            }
            // else: permission granted, still waiting on the sync round-trip — stay unresolved.
        }
    }

    LaunchedEffect(mapView, cameraTarget) {
        val (lat, lng) = cameraTarget ?: return@LaunchedEffect
        mapView.getMapAsync { map ->
            // Instant snap, not an animated fly-in — but the screen itself stays hidden
            // until this fires, so there's nothing visible to snap away from.
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(lat, lng))
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

    // Status (safe/unsure/danger) lives on the flat users/{userId} profile, joined client-side
    // with this group's membership roster.
    var groupMembers by remember { mutableStateOf<List<GroupMember>>(emptyList()) }

    DisposableEffect(groupId) {
        val subscription = UserRepository.observeGroupMembers(groupId) { members ->
            groupMembers = members
        }
        onDispose { UserRepository.removeGroupMembersObserver(subscription) }
    }

    // Raw (uncomposited) square photo per member who has a photoUrl, fetched once per userId —
    // re-fetched only if that member's url actually changes. Members with no photoUrl just keep
    // using the existing generic classification icons (see updateGhostAvatars), nothing changes
    // for them.
    val photoBitmaps = remember { mutableStateMapOf<String, Bitmap>() }
    val loadedPhotoUrls = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(groupMembers) {
        groupMembers.forEach { member ->
            val url = member.photoUrl
            if (url.isNotBlank() && loadedPhotoUrls[member.id] != url) {
                loadedPhotoUrls[member.id] = url
                launch {
                    val bitmap = loadSquareBitmap(context, url, GHOST_ICON_SIZE_PX)
                    if (bitmap != null) photoBitmaps[member.id] = bitmap
                }
            }
        }
    }

    LaunchedEffect(loadedStyle, groupLocations, groupMembers, photoBitmaps.toMap()) {
        val style = loadedStyle ?: return@LaunchedEffect
        updateGhostAvatars(style, context, groupLocations, groupMembers, photoBitmaps)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (cameraTarget == null) {
            // Waiting on the one-shot camera resolution above — same loading look used
            // elsewhere in the app, never the wrong map position.
            DynamicBlueGridBackground()
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapView }
            )
        }
    }
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

// --- Ghost avatars: group locations -> Canvas bitmaps -> GeoJsonSource -----

// IMPORTANT, verified against this project's actual android-sdk 13.7.1 jar:
// MapLibre Native's SymbolLayer/PropertyFactory on Android has no elevation or
// height-offset property (that's a MapLibre GL JS-only feature, gated behind a
// terrain source — it doesn't exist in this native SDK version). Point.fromLngLat
// below DOES store the Z value on the GeoJSON geometry (correct, ready for a
// richer future payload), but MapLibre Android will not use it to move the icon
// up/down on screen. The gray-out / caution-badge treatment is the actual visual
// communication of floor state here, not vertical position.
private fun iconIdFor(classification: FloorClassification, isUnsafe: Boolean): String {
    val base = when (classification) {
        FloorClassification.CONFIDENT_GROUND -> GHOST_ICON_GROUND_ID
        FloorClassification.CONFIDENT_UNDERGROUND -> GHOST_ICON_UNDERGROUND_ID
        FloorClassification.UNCERTAIN -> GHOST_ICON_UNCERTAIN_ID
    }
    return if (isUnsafe) "$base-unsafe" else base
}

private fun addGhostAvatarLayer(style: Style, context: Context) {
    if (style.getLayer(GHOST_LAYER_ID) != null) return

    if (style.getSource(GHOST_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(GHOST_SOURCE_ID))
    }

    // One bitmap per (classification, isUnsafe) combo, registered once — which
    // bitmap a given marker uses is a per-feature "iconId" property (see
    // updateGhostAvatars), not something we regenerate on every update.
    FloorClassification.entries.forEach { classification ->
        listOf(false, true).forEach { isUnsafe ->
            style.addImage(
                iconIdFor(classification, isUnsafe),
                createGhostAvatarBitmap(context, classification, isUnsafe)
            )
        }
    }

    style.addLayer(
        SymbolLayer(GHOST_LAYER_ID, GHOST_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(Expression.get("iconId")),
            // Fixed value, no zoom-stop expression: stays a constant pixel size
            // on screen regardless of zoom level.
            PropertyFactory.iconSize(1f),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        )
    )
}

private fun photoIconIdFor(userId: String, classification: FloorClassification, isUnsafe: Boolean): String =
    "kaze-ghost-photo-$userId-${classification.name}-$isUnsafe"

private fun updateGhostAvatars(
    style: Style,
    context: Context,
    groupLocations: List<GroupLocation>,
    groupMembers: List<GroupMember>,
    photoBitmaps: Map<String, Bitmap>
) {
    val statusByUserId = groupMembers.associateBy({ it.id }, { it.status })
    val memberIds = statusByUserId.keys

    // locations/{userId} is global, not scoped to a group, so only render markers for people
    // actually in this group's roster.
    val features = groupLocations.filter { it.userId in memberIds }.map { location ->
        val status = statusByUserId[location.userId]?.ifBlank { "safe" } ?: "safe"
        val isUnsafe = status.equals("danger", ignoreCase = true)

        val photoBitmap = photoBitmaps[location.userId]
        val iconId = if (photoBitmap != null) {
            // A bad/corrupted cached bitmap should never crash the whole map — fall back to
            // the generic icon for this member instead, same as if no photo existed at all.
            try {
                val id = photoIconIdFor(location.userId, location.classification, isUnsafe)
                // Cheap to re-register every update; MapLibre just overwrites the same id.
                style.addImage(id, compositePhotoAvatarBitmap(context, photoBitmap, location.classification, isUnsafe))
                id
            } catch (e: Exception) {
                Log.w(TAG, "Failed to composite/register photo avatar for ${location.userId}: ${e.message}")
                iconIdFor(location.classification, isUnsafe)
            }
        } else {
            iconIdFor(location.classification, isUnsafe)
        }

        val point = Point.fromLngLat(location.longitude, location.latitude, location.clampedAltitudeMeters)
        val properties = JsonObject().apply {
            addProperty("userId", location.userId)
            addProperty("iconId", iconId)
        }
        Feature.fromGeometry(point, properties)
    }

    // TODO: name label per marker — nice follow-up, not required for this pass.
    (style.getSource(GHOST_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(features))
}

/** Fetches and decodes [url] into a bitmap already cropped/scaled to a [sizePx] square. */
private suspend fun loadSquareBitmap(context: Context, url: String, sizePx: Int): Bitmap? {
    // A photoUrl is whatever someone pasted into the RTDB console — not every link is actually
    // a directly-loadable image (a webpage, a hotlink-blocked page, a dead link, a decode
    // failure). None of that should ever be able to crash the app; worst case, no photo loads
    // and the member just keeps their generic icon.
    return try {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(sizePx, sizePx)
            .scale(Scale.FILL)
            .allowHardware(false) // need a software bitmap to draw into a Canvas below
            .build()

        val result = loader.execute(request)
        val drawable = (result as? SuccessResult)?.drawable ?: return null
        drawable.toBitmap(sizePx, sizePx)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load photo from $url: ${e.message}")
        null
    }
}

/** Same ring/glow/caution treatment as [createGhostAvatarBitmap], with a real photo as the fill. */
private fun compositePhotoAvatarBitmap(
    context: Context,
    sourceBitmap: Bitmap,
    classification: FloorClassification,
    isUnsafe: Boolean
): Bitmap {
    val bitmap = Bitmap.createBitmap(GHOST_ICON_SIZE_PX, GHOST_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val center = GHOST_ICON_SIZE_PX / 2f
    val avatarRadius = GHOST_ICON_SIZE_PX / 2f - 10f
    val isUnderground = classification == FloorClassification.CONFIDENT_UNDERGROUND
    val strokeColor = if (isUnderground) UNDERGROUND_STROKE_COLOR else NightPalette.ACCENT

    if (isUnsafe) {
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor(UNSAFE_GLOW_COLOR)
            style = Paint.Style.STROKE
            strokeWidth = 16f
            maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(center, center, avatarRadius, glowPaint)
    }

    val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(sourceBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    canvas.drawCircle(center, center, avatarRadius, photoPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor(strokeColor)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    canvas.drawCircle(center, center, avatarRadius, strokePaint)

    if (isUnsafe) {
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor(UNSAFE_GLOW_COLOR)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(center, center, avatarRadius, ringPaint)
    }

    if (classification == FloorClassification.UNCERTAIN) {
        drawCautionBadge(context, canvas)
    }

    return bitmap
}

private fun createGhostAvatarBitmap(
    context: Context,
    classification: FloorClassification,
    isUnsafe: Boolean
): Bitmap {
    val bitmap = Bitmap.createBitmap(GHOST_ICON_SIZE_PX, GHOST_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val center = GHOST_ICON_SIZE_PX / 2f
    val avatarRadius = GHOST_ICON_SIZE_PX / 2f - 10f
    val isUnderground = classification == FloorClassification.CONFIDENT_UNDERGROUND

    val fillColor = if (isUnderground) UNDERGROUND_FILL_COLOR else NightPalette.BUILDING_GLASS
    val strokeColor = if (isUnderground) UNDERGROUND_STROKE_COLOR else NightPalette.ACCENT

    if (isUnsafe) {
        // Soft blurred halo drawn first/underneath, so it reads as a glow
        // around the avatar rather than a hard ring.
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor(UNSAFE_GLOW_COLOR)
            style = Paint.Style.STROKE
            strokeWidth = 16f
            maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(center, center, avatarRadius, glowPaint)
    }

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

    if (isUnsafe) {
        // Crisp ring on top of the blurred halo so the red reads clearly even
        // where the blur alone would look too soft.
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor(UNSAFE_GLOW_COLOR)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(center, center, avatarRadius, ringPaint)
    }

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
