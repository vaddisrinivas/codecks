package io.codecks.ui.theme

import org.json.JSONObject

sealed interface ThemeImportResult {
    data class Success(val bundle: ThemeBundle) : ThemeImportResult
    data class Rejected(val reason: Reason) : ThemeImportResult
    enum class Reason { Empty, Oversized, Malformed, UnsupportedVersion, UnknownField, InvalidValue, Unreadable }
}

object ThemeSchemeCodec {
    const val MAX_BYTES = 16 * 1024
    private const val VERSION = 1
    private val bundleKeys = setOf("version", "global", "deck", "trackpad")
    private val schemeKeys = setOf("id", "label", "preset", "opacity", "colors")

    fun encode(bundle: ThemeBundle): String = JSONObject().apply {
        put("version", VERSION)
        put("global", encodeScheme(bundle.global))
        bundle.deck?.let { put("deck", encodeScheme(it)) }
        bundle.trackpad?.let { put("trackpad", encodeScheme(it)) }
    }.toString()

    fun decode(raw: String): ThemeImportResult {
        if (raw.isBlank()) return ThemeImportResult.Rejected(ThemeImportResult.Reason.Empty)
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return ThemeImportResult.Rejected(ThemeImportResult.Reason.Oversized)
        return try {
            val root = JSONObject(raw)
            if (root.keys().asSequence().toSet() - bundleKeys != emptySet<String>()) return ThemeImportResult.Rejected(ThemeImportResult.Reason.UnknownField)
            if (root.optInt("version", -1) != VERSION) return ThemeImportResult.Rejected(ThemeImportResult.Reason.UnsupportedVersion)
            val global = decodeScheme(root.optJSONObject("global") ?: return ThemeImportResult.Rejected(ThemeImportResult.Reason.InvalidValue))
            val deck = root.optJSONObject("deck")?.let(::decodeScheme)
            val trackpad = root.optJSONObject("trackpad")?.let(::decodeScheme)
            val bundle = ThemeBundle(global, deck, trackpad)
            if (!ThemeContrast.isBundleReadable(bundle)) {
                ThemeImportResult.Rejected(ThemeImportResult.Reason.Unreadable)
            } else ThemeImportResult.Success(bundle)
        } catch (_: ThemeValueException) {
            ThemeImportResult.Rejected(ThemeImportResult.Reason.InvalidValue)
        } catch (_: IllegalArgumentException) {
            ThemeImportResult.Rejected(ThemeImportResult.Reason.InvalidValue)
        } catch (_: Exception) {
            ThemeImportResult.Rejected(ThemeImportResult.Reason.Malformed)
        }
    }

    private fun encodeScheme(scheme: ThemeScheme): JSONObject = JSONObject().apply {
        put("id", scheme.id)
        put("label", scheme.label)
        scheme.preset?.let { put("preset", it.stableId) }
        put("opacity", JSONObject().apply {
            put("surface", scheme.opacity.surface.toDouble())
            put("grid", scheme.opacity.grid.toDouble())
            put("glow", scheme.opacity.glow.toDouble())
        })
        put("colors", JSONObject().apply {
            ThemeColorRole.entries.forEach { put(it.name.lowercase(), scheme[it].toHex()) }
        })
    }

    private fun decodeScheme(json: JSONObject): ThemeScheme {
        if (json.keys().asSequence().toSet() - schemeKeys != emptySet<String>()) invalid()
        val colors = json.optJSONObject("colors") ?: invalid()
        val expected = ThemeColorRole.entries.map { it.name.lowercase() }.toSet()
        if (colors.keys().asSequence().toSet() != expected) invalid()
        val opacity = when (val rawOpacity = json.opt("opacity")) {
            null -> invalid()
            is Number -> ThemeOpacity(rawOpacity.toFloat(), rawOpacity.toFloat(), rawOpacity.toFloat())
            is JSONObject -> {
                if (rawOpacity.keys().asSequence().toSet() != setOf("surface", "grid", "glow")) invalid()
                ThemeOpacity(
                    surface = rawOpacity.optDouble("surface", Double.NaN).toFloat(),
                    grid = rawOpacity.optDouble("grid", Double.NaN).toFloat(),
                    glow = rawOpacity.optDouble("glow", Double.NaN).toFloat(),
                )
            }
            else -> invalid()
        }
        return ThemeScheme(
            id = json.optString("id", ""),
            label = json.optString("label", ""),
            preset = json.optString("preset", "").takeIf(String::isNotBlank)?.let { ThemePresetId.fromStableId(it) ?: invalid() },
            opacity = opacity,
            colors = ThemeColorRole.entries.associateWith { role ->
                ThemeArgb.parse(colors.optString(role.name.lowercase(), "")) ?: invalid()
            },
        )
    }

    private fun invalid(): Nothing = throw ThemeValueException()
    private class ThemeValueException : RuntimeException()
}

data class PersistedThemeResolution(
    val bundle: ThemeBundle,
    val issue: ThemeImportResult.Reason? = null,
)

internal fun resolvePersistedTheme(raw: String?): PersistedThemeResolution = when {
    raw == null -> PersistedThemeResolution(ThemeBundle(ThemePresetCatalog.default))
    else -> when (val result = ThemeSchemeCodec.decode(raw)) {
        is ThemeImportResult.Success -> PersistedThemeResolution(result.bundle)
        is ThemeImportResult.Rejected -> PersistedThemeResolution(ThemeBundle(ThemePresetCatalog.default), result.reason)
    }
}

internal fun resolvePersistedThemeBundle(raw: String?): ThemeBundle = resolvePersistedTheme(raw).bundle
