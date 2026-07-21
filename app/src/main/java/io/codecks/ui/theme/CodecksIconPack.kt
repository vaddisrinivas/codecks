package io.codecks.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

enum class CodecksIconPack(
    val label: String,
    val description: String,
) {
    Tabler(
        "Tabler",
        "Crisp technical line icons with strong command-deck character.",
    ),
    Feather(
        "Feather",
        "Light, airy line icons for a softer glass deck.",
    ),
    Material(
        "Material",
        "Familiar Android symbols with filled and outlined utility shapes.",
    ),
    FontAwesome(
        "Font Awesome",
        "Bold, high-recognition symbols with strong app and brand coverage.",
    ),
}

val LocalCodecksIconPack = staticCompositionLocalOf { CodecksIconPack.Tabler }
