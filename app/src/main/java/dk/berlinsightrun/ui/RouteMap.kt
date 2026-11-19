package dk.berlinsightrun.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import dk.berlinsightrun.route.Route
import dk.berlinsightrun.ui.theme.Lime
import dk.berlinsightrun.ui.theme.Navy900
import dk.berlinsightrun.ui.theme.BerlinBlue
import dk.berlinsightrun.ui.theme.StartGreen
import dk.berlinsightrun.ui.theme.StartRed
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

private class MapRefs {
    var runner: Marker? = null
    val poiMarkers = mutableMapOf<Int, Marker>()
    var shownPlayed: Set<Int> = emptySet()
    var startZone: Polygon? = null
}

/**
 * OpenStreetMap-kort med inverterede farver (mørkt kort), ruten i lime og nummererede
 * seværdigheder. [follow] = true holder kortet centreret på løberen.
 */
@Composable
fun RouteMap(
    route: Route,
    modifier: Modifier = Modifier,
    runner: GeoPoint? = null,
    follow: Boolean = false,
    playedPois: Set<Int> = emptySet(),
    showFences: Boolean = false,
    interactive: Boolean = true,
    /** null = vis ikke startzonen; false = rød (ikke ved start endnu); true = grøn. */
    startReached: Boolean? = null,
) {
    val refs = remember { MapRefs() }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(interactive)
                isClickable = interactive
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
                overlayManager.tilesOverlay.setLoadingBackgroundColor(Navy900.toArgb())
                setTilesScaledToDpi(true)
                setBackgroundColor(Navy900.toArgb())

                val points = route.waypoints.map { GeoPoint(it.lat, it.lon) }

                if (showFences) {
                    route.pois.forEach { p ->
                        overlays.add(Polygon(this).apply {
                            setPoints(Polygon.pointsAsCircle(GeoPoint(p.lat, p.lon), p.radiusM))
                            fillPaint.color = 0x262F6FDB
                            outlinePaint.color = 0xAA8DB2F2.toInt()
                            outlinePaint.strokeWidth = 3f
                            setOnClickListener { _, _, _ -> true }
                        })
                    }
                }

                if (startReached != null) {
                    val zone = Polygon(this).apply {
                        setPoints(Polygon.pointsAsCircle(GeoPoint(route.start.lat, route.start.lon), dk.berlinsightrun.run.RunEngine.START_RADIUS_M))
                        outlinePaint.strokeWidth = 4f
                        setOnClickListener { _, _, _ -> true }
                    }
                    refs.startZone = zone
                    overlays.add(zone)
                }

                overlays.add(Polyline(this).apply {
                    setPoints(points)
                    outlinePaint.color = Lime.toArgb()
                    outlinePaint.strokeWidth = 14f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    setOnClickListener { _, _, _ -> true }
                })

                route.pois.forEach { p ->
                    val m = Marker(this).apply {
                        position = GeoPoint(p.lat, p.lon)
                        icon = MapIcons.number(ctx, p.n, played = false)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ -> true }
                    }
                    overlays.add(m)
                    refs.poiMarkers[p.n] = m
                }

                overlays.add(Marker(this).apply {
                    position = GeoPoint(route.start.lat, route.start.lon)
                    icon = MapIcons.flag(ctx)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ -> true }
                })

                val runnerMarker = Marker(this).apply {
                    icon = MapIcons.runner(ctx)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ -> true }
                    isEnabled = false
                }
                overlays.add(runnerMarker)
                refs.runner = runnerMarker

                addOnFirstLayoutListener { _, _, _, _, _ ->
                    if (follow) {
                        controller.setZoom(17.0)
                        controller.setCenter(runner ?: points.first())
                    } else {
                        zoomToBoundingBox(BoundingBox.fromGeoPoints(points).increaseByScale(1.12f), false)
                    }
                }
                onResume()
            }
        },
        update = { map ->
            refs.startZone?.let { z ->
                val c = if (startReached == true) StartGreen.toArgb() else StartRed.toArgb()
                z.fillPaint.color = (c and 0x00FFFFFF) or 0x40000000
                z.outlinePaint.color = c
            }
            refs.runner?.let { m ->
                if (runner != null) {
                    m.position = runner
                    m.isEnabled = true
                    if (follow) map.controller.animateTo(runner)
                }
            }
            if (playedPois != refs.shownPlayed) {
                refs.poiMarkers.forEach { (n, m) -> m.icon = MapIcons.number(map.context, n, played = n in playedPois) }
                refs.shownPlayed = playedPois
            }
            map.invalidate()
        },
        onRelease = { it.onDetach() },
    )
}

object MapIcons {
    private fun px(ctx: Context, dp: Float) = (dp * ctx.resources.displayMetrics.density)

    fun number(ctx: Context, n: Int, played: Boolean): BitmapDrawable {
        val size = px(ctx, 26f).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (played) Lime.toArgb() else BerlinBlue.toArgb() }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Navy900.toArgb(); style = Paint.Style.STROKE; strokeWidth = px(ctx, 2.5f) }
        val r = size / 2f
        c.drawCircle(r, r, r - px(ctx, 1.5f), fill)
        c.drawCircle(r, r, r - px(ctx, 1.5f), ring)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (played) Navy900.toArgb() else 0xFFFFFFFF.toInt()
            textSize = px(ctx, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        c.drawText(n.toString(), r, r - (text.descent() + text.ascent()) / 2, text)
        return BitmapDrawable(ctx.resources, bmp)
    }

    fun runner(ctx: Context): BitmapDrawable {
        val size = px(ctx, 30f).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = size / 2f
        c.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF })
        c.drawCircle(r, r, r * 0.48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        c.drawCircle(r, r, r * 0.48f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BerlinBlue.toArgb(); style = Paint.Style.STROKE; strokeWidth = px(ctx, 3f)
        })
        return BitmapDrawable(ctx.resources, bmp)
    }

    fun flag(ctx: Context): BitmapDrawable {
        val size = px(ctx, 32f).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = size / 2f
        c.drawCircle(r, r, r - px(ctx, 1.5f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Lime.toArgb() })
        c.drawCircle(r, r, r - px(ctx, 1.5f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Navy900.toArgb(); style = Paint.Style.STROKE; strokeWidth = px(ctx, 3f)
        })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Navy900.toArgb(); style = Paint.Style.STROKE; strokeWidth = px(ctx, 2.5f); strokeJoin = Paint.Join.ROUND
        }
        val x0 = r - px(ctx, 5f)
        c.drawLine(x0, r - px(ctx, 8f), x0, r + px(ctx, 8f), p)
        val path = android.graphics.Path().apply {
            moveTo(x0, r - px(ctx, 8f)); lineTo(x0 + px(ctx, 11f), r - px(ctx, 8f))
            lineTo(x0 + px(ctx, 8f), r - px(ctx, 4f)); lineTo(x0 + px(ctx, 11f), r)
            lineTo(x0, r)
        }
        c.drawPath(path, p)
        return BitmapDrawable(ctx.resources, bmp)
    }
}
