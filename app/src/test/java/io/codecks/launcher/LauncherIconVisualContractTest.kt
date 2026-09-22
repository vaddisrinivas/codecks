package io.codecks.launcher

import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconVisualContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun exactExistingRobotDefaultIsPreservedAndSurvivesCommonMasks() {
        val default = file("app/src/main/res/drawable/ic_launcher.png")
        val round = file("app/src/main/res/drawable/ic_launcher_round.png")
        val expected = "a6676149b5d818147ab4df63d3506480027eef6a6dc8995bf24488aa832b28b2"
        assertEquals(expected, sha256(default))
        assertEquals(expected, sha256(round))

        val image = ImageIO.read(default)
        assertEquals(432, image.width)
        assertEquals(432, image.height)
        val masks = listOf<(Double, Double) -> Boolean>(
            { x, y -> x * x + y * y <= 1.0 }, // circle
            { x, y -> abs(x).pow(4) + abs(y).pow(4) <= 1.0 }, // squircle
            { x, y -> abs(x) <= 1.0 && abs(y) <= 1.0 }, // rounded-square safe interior
            { x, y -> x * x + (y + 0.12).pow(2) <= 1.08 }, // teardrop safe body
        )
        masks.forEach { mask ->
            var brightIdentityPixels = 0
            for (y in 108 until 324) for (x in 108 until 324) {
                val nx = (x - 216.0) / 216.0
                val ny = (y - 216.0) / 216.0
                if (mask(nx, ny) && luminance(image.getRGB(x, y)) >= 0.55) {
                    brightIdentityPixels++
                }
            }
            assertTrue("Mask cropped the robot/grid identity", brightIdentityPixels >= 8_000)
        }
    }

    @Test
    fun alternateVectorsDeclareSafeZoneAndMonochromeContract() {
        val slugs = listOf("robot_grid", "pointer_grid", "minimal_green")
        (slugs + "robot_face").forEach { slug ->
            val monochrome = file("app/src/main/res/drawable/ic_launcher_${slug}_monochrome.xml").readText()
            assertSafeZone(monochrome)
            val fills = Regex("fillColor=\"(#[0-9A-Fa-f]{8})\"").findAll(monochrome)
                .map { it.groupValues[1].uppercase() }
                .toSet()
            assertEquals(setOf("#FFFFFFFF"), fills)
        }
        slugs.forEach { slug ->
            assertSafeZone(file("app/src/main/res/drawable/ic_launcher_${slug}_foreground.xml").readText())
        }
    }

    private fun assertSafeZone(xml: String) {
        assertTrue(xml.contains("android:width=\"108dp\""))
        assertTrue(xml.contains("android:height=\"108dp\""))
        assertTrue(xml.contains("android:viewportWidth=\"108\""))
        assertTrue(xml.contains("android:viewportHeight=\"108\""))
        assertTrue(xml.contains("android:name=\"adaptive_safe_zone\""))
        assertTrue(xml.contains("android:pivotX=\"54\""))
        assertTrue(xml.contains("android:pivotY=\"54\""))
        assertTrue(xml.contains("android:scaleX=\"0.68\""))
        assertTrue(xml.contains("android:scaleY=\"0.68\""))
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double = ((argb ushr shift) and 0xff) / 255.0
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun file(relative: String): File = File(root, relative).also {
        assertTrue("Missing $relative", it.isFile)
    }
}
