package app.fork.messenger.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Нужные значки (пути из Material Symbols), чтобы не тянуть
 * целиком библиотеку material-icons ради четырёх штук.
 */
object ForkIcons {
    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack", "M20 11H7.83l5.59,-5.59L12 4l-8 8 8 8 1.41,-1.41L7.83 13H20v-2z")
    }

    val Send: ImageVector by lazy {
        icon("Send", "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z")
    }

    val VolumeOff: ImageVector by lazy {
        icon(
            "VolumeOff",
            "M16.5 12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45 2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zm2.5 0c0 0.94,-0.2 1.82,-0.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89 0.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-0.67 0.52,-1.42 0.93,-2.25 1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81L19.73 21 21 19.73l-9,-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z",
        )
    }

    val PushPin: ImageVector by lazy {
        icon(
            "PushPin",
            "M16 9V4l1 0c0.55 0 1,-0.45 1,-1s-0.45,-1 -1,-1H7c-0.55 0,-1 0.45,-1 1s0.45 1 1 1l1 0v5c0 1.66,-1.34 3,-3 3v2h5.97v7l1 1 1,-1v-7H19v-2c-1.66 0,-3,-1.34 -3,-3z",
        )
    }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
            .build()
}
