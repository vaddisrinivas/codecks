package io.codecks.ui.app

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTokenTest {
    @Test
    fun currentThemeStatusTextTokensMeetNormalTextContrast() {
        statusTextPairs.forEach { pair ->
            assertTrue(
                "${pair.name} contrast ${pair.ratio} must be at least $NORMAL_TEXT_MINIMUM",
                pair.ratio >= NORMAL_TEXT_MINIMUM,
            )
        }
    }

    @Test
    fun currentThemeActionAndIndicatorTokensMeetNonTextContrast() {
        essentialNonTextPairs.forEach { pair ->
            assertTrue(
                "${pair.name} contrast ${pair.ratio} must be at least $LARGE_TEXT_AND_NON_TEXT_MINIMUM",
                pair.ratio >= LARGE_TEXT_AND_NON_TEXT_MINIMUM,
            )
        }
    }

    @Test
    fun writesDeterministicContrastEvidence() {
        val results = JSONArray()
        (statusTextPairs + essentialNonTextPairs).forEach { pair ->
            results.put(
                JSONObject()
                    .put("token", pair.name)
                    .put("ratio", pair.ratio)
                    .put("minimum", pair.minimum)
                    .put("passes", pair.ratio >= pair.minimum),
            )
        }
        val output = evidenceDirectory().resolve("contrast_results.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(results.toString(2))

        assertTrue(output.isFile)
        assertTrue((0 until results.length()).all { results.getJSONObject(it).getBoolean("passes") })
    }

    private fun evidenceDirectory(): File {
        val moduleDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return requireNotNull(moduleDirectory.parentFile).resolve("build/ga-evidence/A11Y-01")
    }

    private data class ContrastPair(
        val name: String,
        val foreground: Long,
        val background: Long,
        val minimum: Double,
    ) {
        val ratio: Double get() = contrastRatio(foreground, background)
    }

    private companion object {
        const val NORMAL_TEXT_MINIMUM = 4.5
        const val LARGE_TEXT_AND_NON_TEXT_MINIMUM = 3.0

        // Exact role pairs consumed by AccessibleStatus from the current light, dark, and OLED themes.
        val statusTextPairs = listOf(
            ContrastPair("light.neutral", 0xFF171C18, 0xFFE5EBE6, NORMAL_TEXT_MINIMUM),
            ContrastPair("light.information", 0xFF00210D, 0xFFC8F5D8, NORMAL_TEXT_MINIMUM),
            ContrastPair("light.success", 0xFF002112, 0xFFB7F1CE, NORMAL_TEXT_MINIMUM),
            ContrastPair("light.warning", 0xFF0A1F13, 0xFFD2E8D9, NORMAL_TEXT_MINIMUM),
            ContrastPair("light.error", 0xFF410002, 0xFFFFDAD6, NORMAL_TEXT_MINIMUM),
            ContrastPair("dark.neutral", 0xFFF1F5F2, 0xFF171D19, NORMAL_TEXT_MINIMUM),
            ContrastPair("dark.information", 0xFFA7F3C5, 0xFF0C3D25, NORMAL_TEXT_MINIMUM),
            ContrastPair("dark.success", 0xFF9FF6BE, 0xFF07512D, NORMAL_TEXT_MINIMUM),
            ContrastPair("dark.warning", 0xFFCBE8D3, 0xFF263D2E, NORMAL_TEXT_MINIMUM),
            ContrastPair("dark.error", 0xFFFFDAD6, 0xFF93000A, NORMAL_TEXT_MINIMUM),
            ContrastPair("oled.neutral", 0xFFF3F7F4, 0xFF111713, NORMAL_TEXT_MINIMUM),
            ContrastPair("oled.information", 0xFFA7F3C5, 0xFF0A3721, NORMAL_TEXT_MINIMUM),
            ContrastPair("oled.success", 0xFF9CF6BD, 0xFF064B29, NORMAL_TEXT_MINIMUM),
            ContrastPair("oled.warning", 0xFFC5E4CE, 0xFF15271C, NORMAL_TEXT_MINIMUM),
            ContrastPair("oled.error", 0xFFFFDAD6, 0xFF93000A, NORMAL_TEXT_MINIMUM),
        )

        val essentialNonTextPairs = listOf(
            ContrastPair("light.action", 0xFF087F3F, 0xFFF7FAF7, LARGE_TEXT_AND_NON_TEXT_MINIMUM),
            ContrastPair("light.error-indicator", 0xFFBA1A1A, 0xFFFFDAD6, LARGE_TEXT_AND_NON_TEXT_MINIMUM),
            ContrastPair("dark.action", 0xFF3DDC84, 0xFF080A09, LARGE_TEXT_AND_NON_TEXT_MINIMUM),
            ContrastPair("dark.error-indicator", 0xFFFFB4AB, 0xFF93000A, LARGE_TEXT_AND_NON_TEXT_MINIMUM),
            ContrastPair("oled.action", 0xFF3DDC84, 0xFF000000, LARGE_TEXT_AND_NON_TEXT_MINIMUM),
            ContrastPair("oled.error-indicator", 0xFFFFB4AB, 0xFF93000A, LARGE_TEXT_AND_NON_TEXT_MINIMUM),
        )

        fun contrastRatio(foreground: Long, background: Long): Double {
            val lighter = maxOf(relativeLuminance(foreground), relativeLuminance(background))
            val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
            return (lighter + 0.05) / (darker + 0.05)
        }

        fun relativeLuminance(argb: Long): Double {
            fun channel(shift: Int): Double {
                val encoded = ((argb shr shift) and 0xFF).toDouble() / 255.0
                return if (encoded <= 0.04045) encoded / 12.92 else Math.pow((encoded + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
    }
}
