package io.codecks.ui.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class ThemePresetId(val stableId: String, val label: String) {
    CodecksGreen("codecks-green", "Codecks Green"),
    Oled("oled", "OLED"),
    LightGlass("light-glass", "Light Glass"),
    Aurora("aurora", "Aurora"),
    Cyber("cyber", "Cyber"),
    Warm("warm", "Warm"),
    Monochrome("monochrome", "Monochrome"),
    HighContrast("high-contrast", "High Contrast");

    companion object {
        fun fromStableId(value: String): ThemePresetId? = entries.firstOrNull { it.stableId == value }
    }
}

enum class ThemeColorRole {
    Primary, Secondary, Tertiary, Surface, Background, Border,
    Success, Warning, Danger, Button, Grid, Glow,
}

enum class ThemeTarget { Global, Deck, Trackpad }
enum class ThemeOpacityRole { Surface, Grid, Glow }

data class ThemeOpacity(
    val surface: Float = 1f,
    val grid: Float = 1f,
    val glow: Float = 1f,
) {
    init { require(listOf(surface, grid, glow).all { it in 0.2f..1f }) }

    operator fun get(role: ThemeOpacityRole): Float = when (role) {
        ThemeOpacityRole.Surface -> surface
        ThemeOpacityRole.Grid -> grid
        ThemeOpacityRole.Glow -> glow
    }

    fun with(role: ThemeOpacityRole, value: Float): ThemeOpacity = when (role) {
        ThemeOpacityRole.Surface -> copy(surface = value)
        ThemeOpacityRole.Grid -> copy(grid = value)
        ThemeOpacityRole.Glow -> copy(glow = value)
    }
}

@JvmInline
value class ThemeArgb private constructor(val value: Long) {
    fun toHex(): String = "#%08X".format(value)

    companion object {
        fun of(value: Long): ThemeArgb? = value
            .takeIf { it in 0..0xFFFF_FFFFL && (it ushr 24) == 0xFFL }
            ?.let(::ThemeArgb)
        fun parse(value: String): ThemeArgb? {
            if (!value.matches(Regex("^#[0-9A-Fa-f]{8}$"))) return null
            return value.drop(1).toLongOrNull(16)?.let(::of)
        }
        fun fromRgb(red: Float, green: Float, blue: Float): ThemeArgb = requireNotNull(
            of(
                0xFF00_0000L or
                    ((red.coerceIn(0f, 1f) * 255).toLong() shl 16) or
                    ((green.coerceIn(0f, 1f) * 255).toLong() shl 8) or
                    (blue.coerceIn(0f, 1f) * 255).toLong(),
            ),
        )
    }
}

data class ThemeScheme(
    val id: String,
    val label: String,
    val preset: ThemePresetId?,
    val colors: Map<ThemeColorRole, ThemeArgb>,
    val opacity: ThemeOpacity = ThemeOpacity(),
) {
    init {
        require(id.matches(Regex("^[a-z0-9][a-z0-9-]{0,47}$")))
        require(label.isNotBlank() && label.length <= 48)
        require(colors.keys == ThemeColorRole.entries.toSet())
    }

    operator fun get(role: ThemeColorRole): ThemeArgb = requireNotNull(colors[role])
    fun withColor(role: ThemeColorRole, color: ThemeArgb): ThemeScheme = copy(colors = colors + (role to color), preset = null)
}

data class ThemeBundle(
    val global: ThemeScheme,
    val deck: ThemeScheme? = null,
    val trackpad: ThemeScheme? = null,
) {
    fun resolve(target: ThemeTarget): ThemeScheme = when (target) {
        ThemeTarget.Global -> global
        ThemeTarget.Deck -> deck ?: global
        ThemeTarget.Trackpad -> trackpad ?: global
    }

    /** Navigation, dialogs, and destructive feedback always come from the global semantic scheme. */
    fun semantic(role: ThemeColorRole): ThemeArgb = global[role]

    fun withScheme(target: ThemeTarget, scheme: ThemeScheme): ThemeBundle = when (target) {
        ThemeTarget.Global -> copy(global = scheme)
        ThemeTarget.Deck -> copy(deck = scheme)
        ThemeTarget.Trackpad -> copy(trackpad = scheme)
    }
}

data class ThemeContrastFeedback(val role: ThemeColorRole, val ratio: Double, val readable: Boolean)

object ThemeContrast {
    const val NORMAL_TEXT_MIN = 4.5
    const val LARGE_TEXT_MIN = 3.0

    fun ratio(foreground: ThemeArgb, background: ThemeArgb): Double {
        val lighter = max(luminance(foreground), luminance(background))
        val darker = min(luminance(foreground), luminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun readableForeground(background: ThemeArgb): ThemeArgb {
        val black = requireNotNull(ThemeArgb.of(0xFF000000))
        val white = requireNotNull(ThemeArgb.of(0xFFFFFFFF))
        return if (ratio(black, background) >= ratio(white, background)) black else white
    }

    fun criticalFeedback(scheme: ThemeScheme): List<ThemeContrastFeedback> = listOf(
        ThemeColorRole.Background,
        ThemeColorRole.Surface,
        ThemeColorRole.Button,
        ThemeColorRole.Danger,
        ThemeColorRole.Warning,
        ThemeColorRole.Success,
    ).map { role ->
        val ratio = ratio(readableForeground(scheme[role]), scheme[role])
        ThemeContrastFeedback(role, ratio, ratio >= NORMAL_TEXT_MIN)
    }

    fun isCriticalReadable(scheme: ThemeScheme): Boolean =
        criticalFeedback(scheme).all { it.readable } &&
            ratio(scheme[ThemeColorRole.Border], scheme[ThemeColorRole.Background]) >= LARGE_TEXT_MIN

    fun isBundleReadable(bundle: ThemeBundle): Boolean =
        listOfNotNull(bundle.global, bundle.deck, bundle.trackpad).all(::isCriticalReadable)

    private fun luminance(color: ThemeArgb): Double {
        fun component(shift: Int): Double {
            val channel = ((color.value shr shift) and 0xFF).toDouble() / 255.0
            return if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * component(16) + 0.7152 * component(8) + 0.0722 * component(0)
    }
}

object ThemeColorMath {
    /** Alpha-blends an opaque role over an opaque surface and always returns an opaque color. */
    fun composite(foreground: ThemeArgb, background: ThemeArgb, opacity: Float): ThemeArgb {
        val alpha = opacity.coerceIn(0f, 1f)
        fun channel(shift: Int): Long {
            val front = (foreground.value shr shift) and 0xFF
            val back = (background.value shr shift) and 0xFF
            return (front * alpha + back * (1f - alpha)).toLong().coerceIn(0, 255)
        }
        return requireNotNull(
            ThemeArgb.of(0xFF00_0000L or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)),
        )
    }
}

object ThemePresetCatalog {
    private fun argb(value: Long) = requireNotNull(ThemeArgb.of(value))
    private fun scheme(id: ThemePresetId, values: LongArray, surfaceOpacity: Float = 1f): ThemeScheme = ThemeScheme(
        id = id.stableId,
        label = id.label,
        preset = id,
        colors = ThemeColorRole.entries.zip(values.map(::argb)).toMap(),
        opacity = ThemeOpacity(surface = surfaceOpacity, grid = surfaceOpacity),
    )

    val presets: List<ThemeScheme> = listOf(
        scheme(ThemePresetId.CodecksGreen, longArrayOf(0xFF3DDC84,0xFF8BC8A4,0xFF66E49A,0xFF0D110F,0xFF000000,0xFF647068,0xFF3DDC84,0xFFFFC857,0xFFFF6B6B,0xFF143221,0xFF1C4A30,0xFF3DDC84)),
        scheme(ThemePresetId.Oled, longArrayOf(0xFFFFFFFF,0xFFBDBDBD,0xFF8AE6B1,0xFF080808,0xFF000000,0xFF707070,0xFF58D68D,0xFFFFC857,0xFFFF7373,0xFF1B1B1B,0xFF292929,0xFFFFFFFF)),
        scheme(ThemePresetId.LightGlass, longArrayOf(0xFF006B3C,0xFF385846,0xFF005F73,0xFFF7FAF7,0xFFFFFFFF,0xFF64736A,0xFF087F3F,0xFF7A5200,0xFFB3261E,0xFFE4F3E9,0xFFD1E9DB,0xFF008A50), .88f),
        scheme(ThemePresetId.Aurora, longArrayOf(0xFF62E6C5,0xFFC2A7FF,0xFFFF8FD8,0xFF171329,0xFF090714,0xFF8476A6,0xFF62E6C5,0xFFFFCC66,0xFFFF7C8E,0xFF2B2247,0xFF223A4A,0xFFB878FF)),
        scheme(ThemePresetId.Cyber, longArrayOf(0xFF00F0FF,0xFFFF3DCE,0xFFC8FF3D,0xFF101521,0xFF05070C,0xFF66758E,0xFF48F29A,0xFFFFD84D,0xFFFF5D73,0xFF18263A,0xFF14364A,0xFF00F0FF)),
        scheme(ThemePresetId.Warm, longArrayOf(0xFFFFB36B,0xFFD7A77B,0xFFF08A7E,0xFF2B201B,0xFF17110E,0xFF8B756A,0xFF74D39A,0xFFFFC857,0xFFFF7A70,0xFF493126,0xFF5A3B2C,0xFFFFA45C)),
        scheme(ThemePresetId.Monochrome, longArrayOf(0xFFFFFFFF,0xFFBDBDBD,0xFF8F8F8F,0xFF171717,0xFF080808,0xFF808080,0xFFE0E0E0,0xFFFFFFFF,0xFFFFFFFF,0xFF292929,0xFF333333,0xFFFFFFFF)),
        scheme(ThemePresetId.HighContrast, longArrayOf(0xFFFFFF00,0xFF00FFFF,0xFFFF66FF,0xFF000000,0xFF000000,0xFFFFFFFF,0xFF00FF66,0xFFFFFF00,0xFFFF4D4D,0xFF111111,0xFF222222,0xFFFFFFFF)),
    )

    val default: ThemeScheme = presets.first()
    fun resolve(id: String?): ThemeScheme? = presets.firstOrNull { it.id == id }
}
