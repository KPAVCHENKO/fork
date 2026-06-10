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

    val Settings: ImageVector by lazy {
        icon(
            "Settings",
            "M19.14 12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94 0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39 0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94l-0.36,-2.54c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24 0,-0.43 0.17,-0.47 0.41l-0.36 2.54c-0.59 0.24,-1.13 0.57,-1.62 0.94l-2.39,-0.96c-0.22,-0.08 -0.47 0,-0.59 0.22L2.74 8.87c-0.12 0.21,-0.08 0.47 0.12 0.61l2.03 1.58c-0.05 0.3,-0.09 0.63,-0.09 0.94s0.02 0.64 0.07 0.94l-2.03 1.58c-0.18 0.14,-0.23 0.41,-0.12 0.61l1.92 3.32c0.12 0.22 0.37 0.29 0.59 0.22l2.39,-0.96c0.5 0.38 1.03 0.7 1.62 0.94l0.36 2.54c0.05 0.24 0.24 0.41 0.48 0.41h3.84c0.24 0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39 0.96c0.22 0.08 0.47 0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61l-2.01,-1.58zM12 15.6c-1.98 0,-3.6 -1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6 3.6 1.62 3.6 3.6 -1.62 3.6 -3.6 3.6z",
        )
    }

    val Download: ImageVector by lazy {
        icon("Download", "M19 9h-4V3H9v6H5l7 7 7,-7zM5 18v2h14v-2H5z")
    }

    val Play: ImageVector by lazy {
        icon("Play", "M8 5v14l11,-7z")
    }

    val Pause: ImageVector by lazy {
        icon("Pause", "M6 19h4V5H6v14zm8,-14v14h4V5h-4z")
    }

    val Attach: ImageVector by lazy {
        icon(
            "Attach",
            "M16.5 6v11.5c0 2.21,-1.79 4,-4 4s-4,-1.79 -4,-4V5c0,-1.38 1.12,-2.5 2.5,-2.5s2.5 1.12 2.5 2.5v10.5c0 0.55,-0.45 1,-1 1s-1,-0.45 -1,-1V6H10v9.5c0 1.38 1.12 2.5 2.5 2.5s2.5,-1.12 2.5,-2.5V5c0,-2.21 -1.79,-4 -4,-4S7 2.79 7 5v12.5c0 3.04 2.46 5.5 5.5 5.5s5.5,-2.46 5.5,-5.5V6h-1.5z",
        )
    }

    val Mic: ImageVector by lazy {
        icon(
            "Mic",
            "M12 14c1.66 0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5,-3c0 2.76,-2.24 5,-5 5s-5,-2.24 -5,-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39,-0.49 6,-3.39 6,-6.92h-2z",
        )
    }

    val Check: ImageVector by lazy {
        icon("Check", "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41,-1.41z")
    }

    val CheckDouble: ImageVector by lazy {
        icon(
            "CheckDouble",
            "M18 7l-1.41,-1.41 -6.34 6.34 1.41 1.41L18 7zm4.24,-1.41L11.66 16.17 7.48 12l-1.41 1.41L11.66 19l12,-12,-1.42,-1.41zM.41 13.41L6 19l1.41,-1.41L1.83 12 .41 13.41z",
        )
    }

    val Clock: ImageVector by lazy {
        icon(
            "Clock",
            "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0,-8,-3.58 -8,-8s3.58,-8 8,-8 8 3.58 8 8,-3.58 8,-8 8zm.5,-13H11v6l5.25 3.15.75,-1.23,-4.5,-2.67z",
        )
    }

    val Reply: ImageVector by lazy {
        icon("Reply", "M10 9V5l-7 7 7 7v-4.1c5 0 8.5 1.6 11 5.1 -1,-5 -4,-10 -11,-11z")
    }

    val Close: ImageVector by lazy {
        icon("Close", "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z")
    }

    val Search: ImageVector by lazy {
        icon(
            "Search",
            "M15.5 14h-0.79l-0.28,-0.27c0.98,-1.14 1.57,-2.62 1.57,-4.23 0,-3.59 -2.91,-6.5 -6.5,-6.5S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09,-0.59 4.23,-1.57l0.27 0.28v0.79l5 4.99L20.49 19l-4.99,-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
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
