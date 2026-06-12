package app.fork.messenger.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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

    val Sticker: ImageVector by lazy {
        icon(
            "Sticker",
            "M5.5 2C3.57 2 2 3.57 2 5.5v13C2 20.43 3.57 22 5.5 22H14l8,-8V5.5C22 3.57 20.43 2 18.5 2h-13zM14 20v-3.5c0,-1.38 1.12,-2.5 2.5,-2.5H20L14 20zm5.5,-8H16.5c-2.48 0,-4.5 2.02,-4.5 4.5V20H5.5C4.67 20 4 19.33 4 18.5v-13C4 4.67 4.67 4 5.5 4h13c0.83 0 1.5 0.67 1.5 1.5V12z",
        )
    }

    val Search: ImageVector by lazy {
        icon(
            "Search",
            "M15.5 14h-0.79l-0.28,-0.27c0.98,-1.14 1.57,-2.62 1.57,-4.23 0,-3.59 -2.91,-6.5 -6.5,-6.5S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09,-0.59 4.23,-1.57l0.27 0.28v0.79l5 4.99L20.49 19l-4.99,-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
        )
    }

    /** Шеврон «вниз» — кнопка прокрутки к последним сообщениям. */
    val Down: ImageVector by lazy {
        icon("Down", "M7.41 8.59L12 13.17l4.59,-4.58L18 10l-6 6,-6,-6z")
    }

    /** Пузырь чата — вкладка «Чаты» в нижней навигации. */
    val Chats: ImageVector by lazy {
        icon(
            "Chats",
            "M20 2H4c-1.1 0,-2 0.9,-2 2v18l4,-4h14c1.1 0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zm-2 12H6v-2h12v2zm0,-3H6V9h12v2zm0,-3H6V6h12v2z",
        )
    }

    /** Силуэт — вкладка «Профиль» в нижней навигации. */
    val Person: ImageVector by lazy {
        icon(
            "Person",
            "M12 12c2.21 0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4 1.79,-4 4 1.79 4 4 4zm0 2c-2.67 0,-8 1.34,-8 4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z",
        )
    }

    /** Клавиатура — переключение панель ⇄ клавиатура в поле ввода. */
    val Keyboard: ImageVector by lazy {
        icon(
            "Keyboard",
            "M20 5H4c-1.1 0,-1.99 0.9,-1.99 2L2 17c0 1.1 0.9 2 2 2h16c1.1 0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zm-9 3h2v2h-2V8zm0 3h2v2h-2v-2zM8 8h2v2H8V8zm0 3h2v2H8v-2zm-1 2H5v-2h2v2zm0,-3H5V8h2v2zm9 7H8v-2h8v2zm0,-4h-2v-2h2v2zm0,-3h-2V8h2v2zm3 3h-2v-2h2v2zm0,-3h-2V8h2v2z",
        )
    }

    /** Фото/галерея — вложение. */
    val Image: ImageVector by lazy {
        icon(
            "Image",
            "M21 19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1 0,-2 0.9,-2 2v14c0 1.1 0.9 2 2 2h14c1.1 0 2,-0.9 2,-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5,-4.5z",
        )
    }

    /** Столбики — опрос. */
    val Poll: ImageVector by lazy {
        icon("Poll", "M5 9.2h3V19H5zM10.6 5h2.8v14h-2.8zm5.6 8H19v6h-2.8z")
    }

    /** Лист с загнутым углом — документ/файл. */
    val File: ImageVector by lazy {
        icon(
            "File",
            "M6 2c-1.1 0,-1.99 0.9,-1.99 2L4 20c0 1.1 0.89 2 1.99 2H18c1.1 0 2,-0.9 2,-2V8l-6,-6H6zm7 7V3.5L18.5 9H13z",
        )
    }

    /** Булавка-метка — геопозиция. */
    val Location: ImageVector by lazy {
        icon(
            "Location",
            "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7zm0 9.5c-1.38 0,-2.5 -1.12,-2.5 -2.5s1.12,-2.5 2.5,-2.5 2.5 1.12 2.5 2.5,-1.12 2.5,-2.5 2.5z",
        )
    }

    /** Значок GIF — вкладка гифок в панели. */
    val Gif: ImageVector by lazy {
        icon(
            "Gif",
            "M11.5 9H13v6h-1.5V9zM9 9H6c-0.6 0,-1 0.5,-1 1v4c0 0.5 0.4 1 1 1h3c0.6 0 1,-0.5 1,-1v-2H8.5v1.5h-2v-3H10V10c0,-0.5 -0.4,-1 -1,-1zm10 1.5V9h-4.5v6H16v-2h2v-1.5h-2v-1.5h3z",
        )
    }

    /** Backspace — удалить последний символ в панели эмодзи. */
    val Backspace: ImageVector by lazy {
        icon(
            "Backspace",
            "M22 3H7c-0.69 0,-1.23 0.35,-1.59 0.88L0 12l5.41 8.11c0.36 0.53 0.9 0.89 1.59 0.89h15c1.1 0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zm-3 12.59L17.59 17 14 13.41 10.41 17 9 15.59 12.59 12 9 8.41 10.41 7 14 10.59 17.59 7 19 8.41 15.41 12 19 15.59z",
        )
    }

    /** Смайлик для поля ввода. */
    val Smile: ImageVector by lazy {
        icon(
            "Smile",
            "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0,-8,-3.58 -8,-8s3.58,-8 8,-8 8 3.58 8 8,-3.58 8,-8 8zm3.5,-9c0.83 0 1.5,-0.67 1.5,-1.5S16.33 8 15.5 8 14 8.67 14 9.5s0.67 1.5 1.5 1.5zm-7 0c0.83 0 1.5,-0.67 1.5,-1.5S9.33 8 8.5 8 7 8.67 7 9.5 7.67 11 8.5 11zm3.5 6.5c2.33 0 4.31,-1.46 5.11,-3.5H6.89c0.8 2.04 2.78 3.5 5.11 3.5z",
        )
    }

    /** Коробка-архив. */
    val Archive: ImageVector by lazy {
        icon(
            "Archive",
            "M20.54 5.23l-1.39,-1.68C18.88 3.21 18.47 3 18 3H6c-0.47 0,-0.88 0.21,-1.16 0.55L3.46 5.23C3.17 5.57 3 6.02 3 6.5V19c0 1.1 0.9 2 2 2h14c1.1 0 2,-0.9 2,-2V6.5c0,-0.48 -0.17,-0.93 -0.46,-1.27zM12 17.5L6.5 12H10v-2h4v2h3.5L12 17.5zM5.12 5l0.81,-1h12l0.94 1H5.12z",
        )
    }

    /** Карандаш — кнопка «новый чат», режим редактирования. */
    val Edit: ImageVector by lazy {
        icon(
            "Edit",
            "M3 17.25V21h3.75L17.81 9.94l-3.75,-3.75L3 17.25zM20.71 7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41 0l-1.83 1.83 3.75 3.75 1.83,-1.83z",
        )
    }

    /**
     * Вилка-логотип одним контуром (stroke 1.6dp) — пустые состояния
     * и бренд на логине (Fork Design Spec §7.9).
     */
    val Group: ImageVector by lazy {
        icon(
            "Group",
            "M16 11c1.66 0 2.99,-1.34 2.99,-3S17.66 5 16 5c-1.66 0,-3 1.34,-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99,-1.34 2.99,-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0,-7 1.17,-7 3.5V19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5zm8 0c-0.29 0,-0.62 0.02,-0.97 0.05 1.16 0.84 1.97 1.97 1.97 3.45V19h6v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z",
        )
    }

    val Megaphone: ImageVector by lazy {
        icon(
            "Megaphone",
            "M18 11v2h4v-2h-4zm-2 6.61c0.96 0.71 2.21 1.65 3.2 2.39 0.4,-0.53 0.8,-1.07 1.2,-1.6 -0.99,-0.74 -2.24,-1.68 -3.2,-2.4 -0.4 0.54,-0.8 1.08,-1.2 1.61zM20.4 5.6c-0.4,-0.53 -0.8,-1.07 -1.2,-1.6 -0.99 0.74,-2.24 1.68,-3.2 2.4 0.4 0.53 0.8 1.07 1.2 1.6 0.96,-0.72 2.21,-1.65 3.2,-2.4zM4 9c-1.1 0,-2 0.9,-2 2v2c0 1.1 0.9 2 2 2h1v4h2v-4h1l5 3V6L8 9H4zm11.5 3c0,-1.33 -0.58,-2.53 -1.5,-3.35v6.69c0.92,-0.81 1.5,-2.01 1.5,-3.34z",
        )
    }

    val Shield: ImageVector by lazy {
        icon(
            "Shield",
            "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4zm0 10.99h7c-0.53 4.12,-3.28 7.79,-7 8.94V12H5V6.3l7,-3.11v8.8z",
        )
    }

    val MoreVert: ImageVector by lazy {
        icon(
            "MoreVert",
            "M12 8c1.1 0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2 0.9,-2 2 0.9 2 2 2zm0 2c-1.1 0,-2 0.9,-2 2s0.9 2 2 2 2,-0.9 2,-2 -0.9,-2 -2,-2zm0 6c-1.1 0,-2 0.9,-2 2s0.9 2 2 2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
        )
    }

    val Trash: ImageVector by lazy {
        icon(
            "Trash",
            "M6 19c0 1.1 0.9 2 2 2h8c1.1 0 2,-0.9 2,-2V7H6v12zM19 4h-3.5l-1,-1h-5l-1 1H5v2h14V4z",
        )
    }

    val Forward: ImageVector by lazy {
        icon("Forward", "M12 8V4l8 8,-8 8v-4H4V8z")
    }

    val Copy: ImageVector by lazy {
        icon(
            "Copy",
            "M16 1H4c-1.1 0,-2 0.9,-2 2v14h2V3h12V1zm3 4H8c-1.1 0,-2 0.9,-2 2v14c0 1.1 0.9 2 2 2h11c1.1 0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zm0 16H8V7h11v14z",
        )
    }

    val ForkMark: ImageVector by lazy {
        strokeIcon("ForkMark", "M8 3v5M12 3v7M16 3v5M8 8c0 2.21 1.79 4 4 4s4,-1.79 4,-4M12 12v9")
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

    private fun strokeIcon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .addPath(
                pathData = addPathNodes(pathData),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}
