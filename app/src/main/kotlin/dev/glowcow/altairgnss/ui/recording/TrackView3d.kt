package dev.glowcow.altairgnss.ui.recording

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.maps.GroundMap
import dev.glowcow.altairgnss.maps.MapLayer
import dev.glowcow.altairgnss.recording.PathPoint
import dev.glowcow.altairgnss.recording.PathStop
import dev.glowcow.altairgnss.ui.components.IconButton48
import dev.glowcow.altairgnss.ui.components.NO_VALUE
import dev.glowcow.altairgnss.ui.components.Stat
import dev.glowcow.altairgnss.ui.components.clock
import dev.glowcow.altairgnss.ui.components.distanceText
import dev.glowcow.altairgnss.ui.components.frosted
import dev.glowcow.altairgnss.ui.components.lengthText
import dev.glowcow.altairgnss.ui.components.speedText
import dev.glowcow.altairgnss.ui.theme.AltairIcons
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.sin

/**
 * The track in space with what goes over it: the speed scale on frosted glass along the bottom,
 * under which the track slides as it turns, and, with [onExpand], a button that opens it full
 * screen. A drag turns and tilts it. When [explorable], the full-screen view, two fingers zoom and
 * move it and a tap on the line shows the figures of the point there. [top] is the speed the scale
 * ends at, m/s; [ground] is the colour it lies on. With [map] the track stands on a real map instead
 * of the bare disc. [topInset] is the status bar the scene runs
 * under: it gets frosted glass of its own, so the clock stays readable over a zoomed track, and
 * [bottomInset] keeps the scale clear of the navigation bar.
 */
@Composable
fun TrackScene(
    path: List<PathPoint>,
    top: Float,
    ground: Color,
    modifier: Modifier = Modifier,
    explorable: Boolean = false,
    stops: List<PathStop> = emptyList(),
    map: GroundMap? = null,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    onExpand: (() -> Unit)? = null,
) {
    val c = AltairTheme.colors
    val scene = rememberGraphicsLayer()
    var height by remember { mutableIntStateOf(0) }
    var legendHeight by remember { mutableIntStateOf(0) }
    // The figures of a picked point stand clear of the status bar and the back button.
    val readoutTop = if (topInset > 0.dp) topInset + 56.dp else 0.dp
    val inset = with(LocalDensity.current) { READOUT_INSET.toPx() }
    val readoutY = with(LocalDensity.current) { readoutTop.toPx() } + inset
    // More points than the eye can tell apart: every n-th is enough for the ribbon.
    val shown = remember(path) { (path.size / MAX_SEGMENTS + 1).let { step -> path.filterIndexed { i, _ -> i % step == 0 || i == path.lastIndex } } }
    var picked by remember(shown) { mutableStateOf<Int?>(null) }

    Box(modifier.onSizeChanged { height = it.height }) {
        TrackView3d(
            shown,
            top,
            picked,
            onPick = { picked = it },
            explorable = explorable,
            stops = stops,
            map = map,
            modifier = Modifier.fillMaxSize().drawWithContent {
                scene.record { this@drawWithContent.drawContent() }
                drawLayer(scene)
            },
        )
        SpeedLegend(
            top,
            Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { legendHeight = it.height }
                .frosted(scene, ground, fade = LEGEND_FADE) { Offset(0f, (height - legendHeight).toFloat()) }
                .padding(start = 16.dp, end = 16.dp, top = LEGEND_FADE + 6.dp, bottom = 14.dp + bottomInset),
        )
        picked?.let { shown.getOrNull(it) }?.let { point ->
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = READOUT_INSET, top = readoutTop + READOUT_INSET)
                    .clip(RoundedCornerShape(14.dp))
                    .frosted(scene, ground) { Offset(inset, readoutY) }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat(stringResource(R.string.label_elapsed), clock(point.elapsedMs), Modifier.width(96.dp))
                    Stat(stringResource(R.string.label_distance), distanceText(point.distance), Modifier.width(96.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat(stringResource(R.string.label_altitude), lengthText(point.altitude), Modifier.width(96.dp))
                    Stat(stringResource(R.string.label_speed), point.speed?.let { speedText(it) } ?: NO_VALUE, Modifier.width(96.dp))
                }
            }
        }
        if (topInset > 0.dp) {
            // Full glass behind the clock; it thins out over the lower part of the bar and is gone by its edge.
            Box(Modifier.fillMaxWidth().height(topInset).frosted(scene, ground, fade = topInset * STATUS_FADE, fadeDown = true) { Offset.Zero })
        }
        if (map != null) {
            // Whose map it is: in the corner of the card, and under the status bar in the middle when full screen.
            Text(
                stringResource(R.string.map_credit),
                color = c.muted,
                fontSize = 10.sp,
                modifier = if (topInset > 0.dp) {
                    Modifier.align(Alignment.TopCenter).padding(top = topInset + 20.dp)
                } else {
                    Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp)
                },
            )
        }
        if (onExpand != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                IconButton48(AltairIcons.Expand, stringResource(R.string.track_fullscreen), tint = c.muted, onClick = onExpand)
            }
        }
    }
}

/** Where the view looks from and how it lays the scene out on [width] by [height], [zoom] times larger and moved by [pan]. */
private class Camera(width: Float, height: Float, yaw: Float, pitch: Float, zoom: Float = 1f, pan: Offset = Offset.Zero) {
    // The ground fits across at any turn, and its near edge reaches under the scale along the bottom.
    private val unit = minOf(width, height) * 0.42f * zoom
    private val centre = Offset(width / 2, height * 0.56f) + pan
    private val turn = Math.toRadians(yaw.toDouble())
    private val tilt = Math.toRadians(pitch.toDouble())

    // x and y are parts of the ground's radius, z a part of the tallest point; the depth grows away from the eye.
    fun depth(x: Double, y: Double) = x * sin(turn) + y * cos(turn)

    /**
     * The same projection for a picture lying on the ground: takes a pixel of it to the screen.
     * [scale] is radii of the ground to a pixel, [cu], [cv] the pixel under the centre; north is up
     * in the picture.
     */
    fun ground(scale: Double, cu: Float, cv: Float): Matrix {
        val a = (unit * scale * cos(turn)).toFloat()
        val c = (unit * scale * sin(turn)).toFloat()
        val b = (-unit * scale * sin(tilt) * sin(turn)).toFloat()
        val d = (unit * scale * sin(tilt) * cos(turn)).toFloat()
        return Matrix().apply {
            values[Matrix.ScaleX] = a
            values[Matrix.SkewY] = b
            values[Matrix.SkewX] = c
            values[Matrix.ScaleY] = d
            values[Matrix.TranslateX] = centre.x - a * cu - c * cv
            values[Matrix.TranslateY] = centre.y - b * cu - d * cv
        }
    }

    fun project(x: Double, y: Double, z: Double): Offset {
        val across = x * cos(turn) - y * sin(turn)
        return Offset(
            centre.x + (across * unit).toFloat(),
            centre.y - (depth(x, y) * sin(tilt) * unit).toFloat() - (z * HEIGHT * cos(tilt) * unit).toFloat(),
        )
    }
}

/**
 * A ribbon standing on the ground and rising to the altitude of each point, coloured by the speed
 * there, with a pause sign where it stood still. A finger moved sideways turns it; moved down it pulls the near side down, towards a view
 * from above. When [explorable],
 * two fingers zoom and move it, a tap on the line picks the point there and a tap elsewhere lets
 * it go. Heights are stretched to read well next to the ground distances.
 */
@Composable
private fun TrackView3d(
    shown: List<PathPoint>,
    top: Float,
    picked: Int?,
    onPick: (Int?) -> Unit,
    explorable: Boolean,
    stops: List<PathStop>,
    map: GroundMap?,
    modifier: Modifier = Modifier,
) {
    val c = AltairTheme.colors
    var yaw by rememberSaveable { mutableFloatStateOf(-35f) }
    var pitch by rememberSaveable { mutableFloatStateOf(28f) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }
    val description = stringResource(R.string.track_description)
    // The ground is a disc that reaches a fifth farther than the farthest point of the track.
    val half = remember(shown) { max(shown.maxOf { hypot(it.east, it.north) } * GROUND_MARGIN, MIN_GROUND) }
    val rise = remember(shown) { max(shown.maxOf { it.up }, MIN_RISE) }
    val points = remember(shown) { shown.map { Triple(it.east / half, it.north / half, it.up / rise) } }

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(explorable) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var moved = Offset.Zero
                    var turning = false
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 && explorable) {
                            zoom = (zoom * event.calculateZoom()).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            event.calculatePan().let {
                                panX += it.x
                                panY += it.y
                            }
                            event.changes.forEach { it.consume() }
                        } else if (fingers == 1) {
                            val change = event.changes.first { it.pressed }
                            val step = change.positionChange()
                            moved += step
                            // A finger that barely moves is a tap, not a turn.
                            if (!turning && moved.getDistance() > viewConfiguration.touchSlop) turning = true
                            if (turning) {
                                yaw += step.x * TURN_PER_PIXEL
                                pitch = (pitch + step.y * TURN_PER_PIXEL).coerceIn(MIN_PITCH, MAX_PITCH)
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .then(
                if (!explorable) Modifier else Modifier.pointerInput(points) {
                    detectTapGestures { tap ->
                        val camera = Camera(size.width.toFloat(), size.height.toFloat(), yaw, pitch, zoom, Offset(panX, panY))
                        // The line itself, or the ribbon under it: the nearest point by the eye, within a finger.
                        val near = points.indices.minBy { i ->
                            val at = camera.project(points[i].first, points[i].second, points[i].third)
                            val foot = camera.project(points[i].first, points[i].second, 0.0)
                            val along = if (tap.y in minOf(at.y, foot.y)..maxOf(at.y, foot.y)) 0f else minOf(abs(tap.y - at.y), abs(tap.y - foot.y))
                            hypot(tap.x - at.x, along)
                        }
                        val at = camera.project(points[near].first, points[near].second, points[near].third)
                        val foot = camera.project(points[near].first, points[near].second, 0.0)
                        val within = abs(tap.x - at.x) <= TOUCH.toPx() && tap.y in (minOf(at.y, foot.y) - TOUCH.toPx())..(maxOf(at.y, foot.y) + TOUCH.toPx())
                        onPick(if (within) near else null)
                    }
                },
            ),
    ) {
        val camera = Camera(size.width, size.height, yaw, pitch, zoom, Offset(panX, panY))

        if (map != null) {
            // The ground is the map: sharp under the track, and from the rim of the disc on it gives
            // way to a blurred one that runs far out before it dissolves.
            // The map is made for a light page; on a dark one it is dimmed.
            val tone = if (c.isDark) MAP_DIM else null
            mapLayer(map.far, camera, half, whole = map.far.reach * FAR_WHOLE, gone = map.far.reach, tone)
            mapLayer(map.sharp, camera, half, whole = half, gone = minOf(map.sharp.reach, half * SHARP_GONE), tone)
        } else {
            // The ground: a disc with a grid across it.
            val rim = Path().apply {
                for (i in 0..RIM_STEPS) {
                    val angle = 2 * PI * i / RIM_STEPS
                    camera.project(cos(angle), sin(angle), 0.0).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) }
                }
                close()
            }
            drawPath(rim, c.muted.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx()))
            for (i in 1 until GRID) {
                val at = -1.0 + 2.0 * i / GRID
                // A line of the grid is a chord of the disc.
                val reach = sqrt(1 - at * at)
                drawLine(c.line, camera.project(at, -reach, 0.0), camera.project(at, reach, 0.0), 1.dp.toPx())
                drawLine(c.line, camera.project(-reach, at, 0.0), camera.project(reach, at, 0.0), 1.dp.toPx())
            }
        }

        // The far side first, so the near part of the ribbon covers it.
        val order = (0 until points.lastIndex).sortedByDescending { i ->
            camera.depth((points[i].first + points[i + 1].first) / 2, (points[i].second + points[i + 1].second) / 2)
        }
        for (i in order) {
            val (ax, ay, az) = points[i]
            val (bx, by, bz) = points[i + 1]
            val colour = speedColour(shown[i + 1].speed ?: shown[i].speed, top, c.muted)
            val a = camera.project(ax, ay, az)
            val b = camera.project(bx, by, bz)
            val wall = Path().apply {
                val foot = camera.project(ax, ay, 0.0)
                moveTo(foot.x, foot.y)
                lineTo(a.x, a.y)
                lineTo(b.x, b.y)
                camera.project(bx, by, 0.0).let { lineTo(it.x, it.y) }
                close()
            }
            drawPath(wall, colour.copy(alpha = 0.3f))
            drawLine(c.muted.copy(alpha = 0.45f), camera.project(ax, ay, 0.0), camera.project(bx, by, 0.0), 1.dp.toPx())
            drawLine(colour, a, b, 3.dp.toPx(), StrokeCap.Round)
        }

        // Where it stood still: a pause sign on the line.
        for (stop in stops) {
            val at = camera.project(stop.at.east / half, stop.at.north / half, stop.at.up / rise)
            val r = 7.dp.toPx()
            drawCircle(c.group, r, at)
            drawCircle(c.text, r, at, style = Stroke(1.5.dp.toPx()))
            for (side in listOf(-1, 1)) {
                val x = at.x + side * 2.3.dp.toPx()
                drawLine(c.text, Offset(x, at.y - 3.dp.toPx()), Offset(x, at.y + 3.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            }
        }

        // Where it began, hollow, and where it ended, solid.
        val start = points.first().let { camera.project(it.first, it.second, it.third) }
        val end = points.last().let { camera.project(it.first, it.second, it.third) }
        drawCircle(c.group, 5.dp.toPx(), start)
        drawCircle(c.text, 5.dp.toPx(), start, style = Stroke(2.dp.toPx()))
        drawCircle(c.group, 6.5.dp.toPx(), end)
        drawCircle(c.text, 4.5.dp.toPx(), end)

        // The point picked: a pole from the ground and a dot in its speed's colour.
        picked?.let { points.getOrNull(it) }?.let { (x, y, z) ->
            val at = camera.project(x, y, z)
            drawLine(c.text, camera.project(x, y, 0.0), at, 1.5.dp.toPx())
            drawCircle(c.group, 8.dp.toPx(), at)
            drawCircle(speedColour(shown[picked].speed, top, c.muted), 6.dp.toPx(), at)
            drawCircle(c.text, 8.dp.toPx(), at, style = Stroke(2.dp.toPx()))
        }
    }
}

/** One layer of the map on the ground: whole within [whole] metres of the centre, faded out by [gone]. */
private fun DrawScope.mapLayer(layer: MapLayer, camera: Camera, half: Double, whole: Double, gone: Double, tone: ColorFilter?) {
    val centre = Offset(layer.centreX, layer.centreY)
    val mask = Brush.radialGradient(
        0f to Color.Black,
        (whole / gone).toFloat().coerceIn(0f, 0.99f) to Color.Black,
        1f to Color.Transparent,
        center = centre,
        radius = (gone / layer.metresPerPixel).toFloat(),
    )
    val bounds = Size(layer.image.width.toFloat(), layer.image.height.toFloat())
    withTransform({ transform(camera.ground(layer.metresPerPixel / half, layer.centreX, layer.centreY)) }) {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, bounds), Paint())
            drawImage(layer.image, colorFilter = tone, filterQuality = FilterQuality.Medium)
            drawRect(mask, size = bounds, blendMode = BlendMode.DstIn)
            canvas.restore()
        }
    }
}

/** The colours of the track from standing still to [top] m/s. */
@Composable
private fun SpeedLegend(top: Float, modifier: Modifier = Modifier) {
    val c = AltairTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).drawBehind { drawRect(Brush.horizontalGradient(SPEED_COLOURS)) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("0", color = c.muted, fontSize = 11.sp)
            Text(speedText(top), color = c.muted, fontSize = 11.sp)
        }
    }
}

/** A point without a speed, where the receiver had no fix, takes [none]. */
private fun speedColour(speed: Float?, top: Float, none: Color): Color {
    if (speed == null) return none
    val at = (speed / top).coerceIn(0f, 1f) * SPEED_COLOURS.lastIndex
    val from = at.toInt().coerceAtMost(SPEED_COLOURS.lastIndex - 1)
    return lerp(SPEED_COLOURS[from], SPEED_COLOURS[from + 1], at - from)
}

/** Slow to fast: blue, teal, yellow, red. */
private val SPEED_COLOURS = listOf(Color(0xFF2F6FED), Color(0xFF1FB5A3), Color(0xFFF2C230), Color(0xFFE5484D))

private const val MAX_SEGMENTS = 400
private const val GRID = 4

// The least radius of the ground and the least rise the heights are scaled to, metres.
internal const val MIN_GROUND = 10.0
private const val MIN_RISE = 5.0

// How tall the tallest point stands, as a part of the ground's radius.
private const val HEIGHT = 0.75

// How far the ground reaches beyond the farthest point of the track.
internal const val GROUND_MARGIN = 1.2
private const val RIM_STEPS = 96

/** The map for a dark page: a little over half of its colour and half of its light. */
private val MAP_DIM = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(0.55f)
        timesAssign(ColorMatrix().apply { setToScale(0.5f, 0.5f, 0.5f, 1f) })
    },
)

// The blurred map is whole over this part of its reach; the sharp one is gone this many radii out.
private const val FAR_WHOLE = 0.6
private const val SHARP_GONE = 1.6

private const val TURN_PER_PIXEL = 0.25f
private const val MIN_PITCH = 8f
private const val MAX_PITCH = 85f
private const val MIN_ZOOM = 0.6f
private const val MAX_ZOOM = 8f

// How far from the line a tap still picks it, and how much of the scale's glass comes in gradually.
private val TOUCH = 28.dp
private val LEGEND_FADE = 22.dp
private val READOUT_INSET = 10.dp

// The part of the status bar over which its glass fades out.
private const val STATUS_FADE = 0.45f
