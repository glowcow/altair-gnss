package dev.glowcow.altairgnss.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Line icons on a 24×24 viewport. */
object AltairIcons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0Z"

    private fun icon(name: String, stroke: List<String> = emptyList(), fill: List<String> = emptyList(), width: Float = 1.8f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            fill.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) }
            stroke.forEach {
                addPath(
                    PathParser().parsePathString(it).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    // A satellite: the body, two solar panels on struts, and a signal going down to the ground.
    val Status = icon(
        "status",
        stroke = listOf(
            "M12 9l3 3-3 3-3-3z",
            "M9.15 6.89L6.89 9.15 3.35 5.61 5.61 3.35z",
            "M20.65 18.39l-2.26 2.26-3.54-3.54 2.26-2.26z",
            "M8.6 8.6l1.9 1.9M13.5 13.5l1.9 1.9",
            "M5 15a4 4 0 0 1 4 4",
        ),
        fill = listOf(circle(5f, 19f, 1.1f)),
        width = 1.7f,
    )
    val Record = icon("record", stroke = listOf(circle(12f, 12f, 8.5f)), fill = listOf(circle(12f, 12f, 3.6f)))
    val Altimeter = icon("altimeter", listOf("M2.5 19.5L9 8l4 6.5 2.5-3.5 6 8.5z"))
    val Instruments = icon("instruments", listOf(circle(12f, 12f, 8.5f), "M15.5 8.5l-2 5-5 2 2-5z"))
    val Settings = icon("settings", listOf("M4 7h9M17 7h3M4 17h3M11 17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f)))
    val Expand = icon("expand", listOf("M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5"), width = 2f)
    val Back = icon("back", listOf("M19 12H5M11 6l-6 6 6 6"), width = 2f)
    val Chevron = icon("chevron", listOf("M9 5l7 7-7 7"))
    val Check = icon("check", listOf("M5 12.5l4.5 4.5L19 7.5"), width = 2.2f)
}
