package io.codecks.launcher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconArchitectureTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun productionManifestHasOneDefaultLauncherAndAliasOnlyOwnership() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        val mainActivity = manifest.substringAfter("android:name=\".MainActivity\"").substringBefore("</activity>")
        assertFalse(mainActivity.contains("android.intent.category.LAUNCHER"))

        val aliases = Regex("<activity-alias[\\s\\S]*?</activity-alias>").findAll(manifest).map { it.value }.toList()
        assertEquals(4, aliases.size)
        LauncherIcon.entries.forEach { icon ->
            val alias = aliases.single { it.contains("android:name=\"${icon.componentClassName}\"") }
            assertTrue(alias.contains("android:targetActivity=\".MainActivity\""))
            assertTrue(alias.contains("android.intent.category.LAUNCHER"))
            assertTrue(alias.contains("android:exported=\"true\""))
            assertEquals(
                icon == LauncherIcon.RobotFace,
                alias.contains("android:enabled=\"true\""),
            )
        }
        assertEquals(1, aliases.count { it.contains("android:enabled=\"true\"") })
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher_robot_face\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_robot_face_round\""))
    }

    @Test
    fun everyIconHasLegacyAdaptiveMonochromeAndSurfaceAssets() {
        val slugs = listOf("robot_face", "robot_grid", "pointer_grid", "minimal_green")
        slugs.forEach { slug ->
            assertFile("app/src/main/res/mipmap-anydpi/ic_launcher_$slug.xml")
            assertFile("app/src/main/res/mipmap-anydpi-v26/ic_launcher_$slug.xml")
            val themed = assertFile("app/src/main/res/mipmap-anydpi-v33/ic_launcher_$slug.xml").readText()
            assertTrue(themed.contains("<monochrome"))
            assertFile("app/src/main/res/drawable/ic_launcher_${slug}_monochrome.xml")
            if (slug != "robot_face") {
                assertFile("app/src/main/res/drawable/ic_launcher_${slug}_foreground.xml")
            }
        }
        val aliases = assertFile("app/src/main/res/values/launcher_icon_colors.xml").readText()
        slugs.forEach { slug ->
            assertTrue(aliases.contains("ic_launcher_${slug}_round"))
            assertTrue(aliases.contains("ic_widget_$slug"))
            assertTrue(aliases.contains("ic_notification_$slug"))
        }
        assertTrue(aliases.contains("ic_splash_shared_robot_face"))
        assertFalse(aliases.contains("ic_splash_robot_grid"))
        val theme = assertFile("app/src/main/res/values-v31/themes.xml").readText()
        assertTrue(theme.contains("@drawable/ic_splash_shared_robot_face"))

        val robotLegacy = assertFile("app/src/main/res/mipmap-anydpi/ic_launcher_robot_face.xml").readText()
        assertTrue(robotLegacy.contains("@drawable/ic_launcher"))
        listOf("mipmap-anydpi-v26", "mipmap-anydpi-v33").forEach { directory ->
            val robot = assertFile("app/src/main/res/$directory/ic_launcher_robot_face.xml").readText()
            assertTrue(robot.contains("@drawable/ic_launcher_robot_face_adaptive"))
        }
        val robotAdaptive = assertFile("app/src/main/res/drawable/ic_launcher_robot_face_adaptive.xml").readText()
        assertTrue(robotAdaptive.contains("android:drawable=\"@drawable/ic_launcher\""))
        assertTrue(robotAdaptive.contains("android:inset=\"16dp\""))
    }

    @Test
    fun switchOrderingCannotDisableTheLastLauncherFirst() {
        val source = File(root, "app/src/main/java/io/codecks/launcher/LauncherIconManager.kt").readText()
        val applySelection = source.substringAfter("private fun applySelection").substringBefore("private fun recoverDefault")
        assertTrue(applySelection.indexOf("setEnabled(icon, true)") < applySelection.indexOf("filterNot { it == icon }"))
        assertTrue(applySelection.contains("converged.reliable && converged.icons == setOf(icon)"))
        val recovery = source.substringAfter("private fun recoverDefault").substringBefore("private fun enabledIcons")
        assertTrue(recovery.indexOf("if (!LauncherIconPolicy.mayDisableAlternatives") < recovery.indexOf("filterNot { it == fallback }"))
        assertTrue(source.contains("PackageManager.DONT_KILL_APP"))
        assertFalse(source.contains("clearApplicationUserData"))
        assertTrue(source.contains("fun current(): LauncherIcon = runCatching"))
        assertTrue(source.contains("fun reconcile(): LauncherIcon = runCatching"))
        assertTrue(source.contains("if (!previouslyEnabled.reliable) return"))
        val application = assertFile("app/src/main/java/io/codecks/CodecksApplication.kt").readText()
        val receiver = assertFile("app/src/main/java/io/codecks/launcher/LauncherIconRecoveryReceiver.kt").readText()
        assertTrue(application.contains("runCatching { LauncherIconManager(this).reconcile() }"))
        assertTrue(receiver.contains("runCatching { LauncherIconManager(context.applicationContext).reconcile() }"))
    }

    @Test
    fun selectorExposesOneRadioGroupAndAboutNamesTheSelection() {
        val source = assertFile("app/src/main/java/io/codecks/ui/settings/SettingsScreen.kt").readText()
        val panel = source.substringAfter("private fun LauncherIconPanel").substringBefore("private fun IconPackPanel")
        assertTrue(panel.contains("Modifier.selectableGroup()"))
        assertTrue(panel.contains("role = Role.RadioButton"))
        assertTrue(panel.contains("selected = launcherIcon == icon"))
        assertTrue(panel.contains("contentDescription = null"))
        assertTrue(source.contains("${'$'}appVersionLabel · ${'$'}{launcherIcon.label} icon"))
        assertTrue(source.contains("LauncherIcon.RobotFace -> R.drawable.ic_launcher"))
    }

    @Test
    fun policyDocumentsProvenanceSharedSurfacesAndMacHelperBoundary() {
        val policy = assertFile("docs/ux/LAUNCHER_ICON_POLICY.md").readText()
        assertTrue(policy.contains("a6676149b5d818147ab4df63d3506480027eef6a6dc8995bf24488aa832b28b2"))
        assertTrue(policy.contains("Apache-2.0"))
        assertTrue(policy.contains("no Apple, OpenAI, Android robot"))
        assertTrue(policy.contains("shared default robot-face splash"))
        assertTrue(policy.contains("Mac helper bundle/Dock icon is not"))
        assertFile("scripts/verify_launcher_manifest_matrix.py")
        assertFile("scripts/verify_launcher_icon_pixels.py")
    }

    @Test
    fun protectedApplicationIdentityAndNoShrinkRemainUnchanged() {
        val build = File(root, "app/build.gradle.kts").readText()
        assertTrue(build.contains("applicationId = \"app.codecks\""))
        assertTrue(build.contains("isMinifyEnabled = false"))
        assertTrue(build.contains("isShrinkResources = false"))
    }

    private fun assertFile(relative: String): File = File(root, relative).also { file ->
        assertTrue("Missing $relative", file.isFile)
    }
}
