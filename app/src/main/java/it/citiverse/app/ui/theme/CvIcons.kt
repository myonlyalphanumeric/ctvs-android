package it.citiverse.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icone a tratto (24×24) prese dallo stile del sito: frecce dei voti, commenti,
 * condividi, più qualche icona per sezioni e fediverso.
 */
object CvIcons {
    private fun stroke(name: String, vararg paths: String, filled: Boolean = false): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { d ->
            b.addPath(
                pathData = addPathNodes(d),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private const val UP = "M12 3.5 3.5 12.5H8.5V20h7v-7.5h5Z"
    private const val DOWN = "M12 20.5 3.5 11.5H8.5V4h7v7.5h5Z"

    val Up = stroke("up", UP)
    val UpFilled = stroke("upFilled", UP, filled = true)
    val Down = stroke("down", DOWN)
    val DownFilled = stroke("downFilled", DOWN, filled = true)
    val Comment = stroke("comment", "M20.5 11.5a8 8 0 0 1-11.6 7.2L3.5 20.5l1.6-4.9A8 8 0 1 1 20.5 11.5Z")
    val Share = stroke("share", "M4 13v6a1.5 1.5 0 0 0 1.5 1.5h13A1.5 1.5 0 0 0 20 19v-6M16 7l-4-4-4 4M12 3v12.5")
    val Clock = stroke("clock", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z", "M12 7v5l3 2")
    val Fire = stroke("fire", "M12 21c-3.9 0-7-2.8-7-6.6 0-2.9 1.9-4.9 3.4-6.4.3 1.9 1.3 3 2.4 3.4C10.3 8 11.4 5 14 3c0 3.2 5 5.2 5 11.4 0 3.8-3.1 6.6-7 6.6Z")
    val Trend = stroke("trend", "M3 17l6-6 4 4 8-8", "M15 7h6v6")
    val Star = stroke("star", "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.8-5.2 2.8 1-5.8-4.3-4.1 5.9-.9Z")
    val Globe = stroke(
        "globe",
        "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z",
        "M3 12h18",
        "M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3Z",
    )
    val Copy = stroke("copy", "M9 9h10.5v11.5H9Z", "M15 9V4.5H4.5V15H9")
    val Compass = stroke("compass", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z", "M15.5 8.5l-2 5-5 2 2-5Z")
    val Home = stroke("home", "M4 10.5 12 4l8 6.5V20h-5v-6h-6v6H4Z")
    val Bell = stroke("bell", "M18 16V11a6 6 0 1 0-12 0v5l-2 2h16Z", "M10 20.5a2 2 0 0 0 4 0")
    val BellFilled = stroke("bellFilled", "M18 16V11a6 6 0 1 0-12 0v5l-2 2h16Z", "M10 20.5a2 2 0 0 0 4 0", filled = true)
    val User = stroke("user", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z", "M4.5 20.5c1.2-3.6 4-5.5 7.5-5.5s6.3 1.9 7.5 5.5")
    val Search = stroke("search", "M10.5 18a7.5 7.5 0 1 0 0-15 7.5 7.5 0 0 0 0 15Z", "M16 16l5 5")
    val Reply = stroke("reply", "M9.5 5 3.5 11l6 6", "M4 11h9.5a7 7 0 0 1 7 7v1")
    val Send = stroke("send", "M4 12 20.5 4l-4 16.5-4.3-6.7Z", "M12.2 13.8 20.5 4")
    val Plus = stroke("plus", "M12 5v14", "M5 12h14")
    val Check = stroke("check", "M5 12.5l4.5 4.5L19 7.5")
    val Folder = stroke("folder", "M3.5 6.5h6l2 2h9v10h-17Z")
    val External = stroke("external", "M14 4h6v6", "M20 4l-9 9", "M18 14v5.5H4.5V6H10")
    val Battery = stroke("battery", "M4 7h14v10H4Z", "M21 10.5v3")
    val Help = stroke("help", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z", "M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6v.6", "M12 17h.01")
}
