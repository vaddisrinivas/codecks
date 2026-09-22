package io.codecks.ui.theme

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal fun canonicalThemeName(value: String): String = value.lowercase(Locale.ROOT)

data class NamedTheme(
    val id: String,
    val name: String,
    val bundle: ThemeBundle,
) {
    init {
        require(id.matches(Regex("^[a-z0-9][a-z0-9-]{0,47}$")))
        require(name.trim() == name && name.isNotBlank() && name.length <= ThemeLibraryCodec.MAX_NAME_CHARS)
        require(ThemeContrast.isBundleReadable(bundle))
    }
}

data class ThemeLibraryState(val themes: List<NamedTheme> = emptyList()) {
    init {
        require(themes.size <= ThemeLibraryCodec.MAX_THEMES)
        require(themes.map(NamedTheme::id).distinct().size == themes.size)
        require(themes.map { canonicalThemeName(it.name) }.distinct().size == themes.size)
    }

    fun save(theme: NamedTheme): ThemeLibraryState {
        require(themes.none { it.id != theme.id && canonicalThemeName(it.name) == canonicalThemeName(theme.name) })
        val retained = themes.filterNot { it.id == theme.id }
        require(retained.size < ThemeLibraryCodec.MAX_THEMES)
        return ThemeLibraryState((listOf(theme) + retained).take(ThemeLibraryCodec.MAX_THEMES))
    }

    fun rename(id: String, name: String): ThemeLibraryState {
        require(themes.any { it.id == id })
        val clean = ThemeLibraryCodec.cleanName(name) ?: throw IllegalArgumentException("invalid theme name")
        require(themes.none { it.id != id && canonicalThemeName(it.name) == canonicalThemeName(clean) })
        return ThemeLibraryState(themes.map { if (it.id == id) it.copy(name = clean) else it })
    }

    fun delete(id: String): ThemeLibraryState {
        require(themes.any { it.id == id })
        return ThemeLibraryState(themes.filterNot { it.id == id })
    }
}

sealed interface ThemeLibraryDecodeResult {
    data class Success(val state: ThemeLibraryState, val migrated: Boolean = false) : ThemeLibraryDecodeResult
    data class Rejected(val reason: Reason) : ThemeLibraryDecodeResult
    enum class Reason { Empty, Oversized, Malformed, UnsupportedVersion, UnknownField, InvalidValue }
}

object ThemeLibraryCodec {
    const val MAX_THEMES = 12
    const val MAX_NAME_CHARS = 40
    const val MAX_BYTES = 64 * 1024
    private const val VERSION = 2

    fun cleanName(raw: String): String? = raw.trim()
        .takeIf { it.isNotEmpty() && it.length <= MAX_NAME_CHARS && it.none(Char::isISOControl) }

    fun encode(state: ThemeLibraryState): String = JSONObject().apply {
        put("version", VERSION)
        put("themes", JSONArray().apply {
            state.themes.forEach { theme ->
                put(JSONObject().apply {
                    put("id", theme.id)
                    put("name", theme.name)
                    put("bundle", JSONObject(ThemeSchemeCodec.encode(theme.bundle)))
                })
            }
        })
    }.toString()

    fun decode(raw: String): ThemeLibraryDecodeResult {
        if (raw.isBlank()) return ThemeLibraryDecodeResult.Rejected(ThemeLibraryDecodeResult.Reason.Empty)
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return ThemeLibraryDecodeResult.Rejected(ThemeLibraryDecodeResult.Reason.Oversized)
        return try {
            val root = JSONObject(raw)
            if (root.keys().asSequence().toSet() != setOf("version", "themes")) reject(ThemeLibraryDecodeResult.Reason.UnknownField)
            val version = root.opt("version") as? Int
                ?: reject(ThemeLibraryDecodeResult.Reason.InvalidValue)
            if (version !in 1..VERSION) reject(ThemeLibraryDecodeResult.Reason.UnsupportedVersion)
            val values = root.optJSONArray("themes") ?: reject(ThemeLibraryDecodeResult.Reason.InvalidValue)
            if (values.length() > MAX_THEMES) reject(ThemeLibraryDecodeResult.Reason.InvalidValue)
            val themes = (0 until values.length()).map { index ->
                val value = values.optJSONObject(index) ?: reject(ThemeLibraryDecodeResult.Reason.InvalidValue)
                val expected = if (version == 1) setOf("id", "label", "bundle") else setOf("id", "name", "bundle")
                if (value.keys().asSequence().toSet() != expected) reject(ThemeLibraryDecodeResult.Reason.UnknownField)
                val rawId = value.opt("id") as? String
                    ?: reject(ThemeLibraryDecodeResult.Reason.InvalidValue)
                val rawName = value.opt(if (version == 1) "label" else "name") as? String
                    ?: reject(ThemeLibraryDecodeResult.Reason.InvalidValue)
                val name = cleanName(rawName)
                    ?: reject(ThemeLibraryDecodeResult.Reason.InvalidValue)
                val bundleJson = value.optJSONObject("bundle") ?: reject(ThemeLibraryDecodeResult.Reason.InvalidValue)
                val bundle = when (val decoded = ThemeSchemeCodec.decode(bundleJson.toString())) {
                    is ThemeImportResult.Success -> decoded.bundle
                    is ThemeImportResult.Rejected -> reject(ThemeLibraryDecodeResult.Reason.InvalidValue)
                }
                NamedTheme(rawId, name, bundle)
            }
            ThemeLibraryDecodeResult.Success(ThemeLibraryState(themes), migrated = version != VERSION)
        } catch (rejected: RejectedValue) {
            ThemeLibraryDecodeResult.Rejected(rejected.reason)
        } catch (_: IllegalArgumentException) {
            ThemeLibraryDecodeResult.Rejected(ThemeLibraryDecodeResult.Reason.InvalidValue)
        } catch (_: Exception) {
            ThemeLibraryDecodeResult.Rejected(ThemeLibraryDecodeResult.Reason.Malformed)
        }
    }

    private fun reject(reason: ThemeLibraryDecodeResult.Reason): Nothing = throw RejectedValue(reason)
    private class RejectedValue(val reason: ThemeLibraryDecodeResult.Reason) : RuntimeException()
}
