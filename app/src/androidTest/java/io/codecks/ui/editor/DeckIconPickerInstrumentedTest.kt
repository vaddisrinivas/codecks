package io.codecks.ui.editor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.codecks.MainActivity
import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.ui.designsystem.DeckControlTile
import io.codecks.ui.icons.imageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckIconPickerInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun roundedSearchFavoriteAndSelectionRemainAccessibleAtLargeText() {
        val selected = mutableStateOf(ActionIcon.Empty)
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                    MaterialTheme {
                        Surface(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            DeckIconPicker(selected.value, { selected.value = it })
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Rounded").performScrollTo().performClick()
        rule.onNodeWithText("Find icon").performTextInput("terminal")
        rule.onNodeWithContentDescription(
            "Favorite Terminal",
            useUnmergedTree = true,
        ).performScrollTo().performClick()
        rule.onNodeWithContentDescription(
            "Remove Terminal from favorites",
            useUnmergedTree = true,
        ).assertExists()
        rule.onNodeWithContentDescription("Use Terminal icon").performClick()
        rule.runOnIdle { assertEquals(ActionIcon.Terminal, selected.value) }
    }

    @Test
    fun routinePreviewInstallAndUndoRestoreExactSlots() {
        fun action(id: String) = DeckAction(id, id.replace('_', ' '), ActionKind.Local, ActionIcon.Apps)
        val ids = listOf("coding_start", "terminal", "github", "dev_tools", "chatgpt", "screenshot", "copy", "paste")
        val catalog = ids.map(::action)
        val original = listOf<DeckAction?>(action("original_one"), action("original_two"), null, null, null, null, null, null)
        val slots = mutableStateOf(original)
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize()) {
                        RoutineBankPanel(
                            slots = slots.value,
                            allActions = catalog,
                            onAssign = { index, action -> slots.value = slots.value.toMutableList().also { it[index] = action } },
                            onRemove = { index -> slots.value = slots.value.toMutableList().also { it[index] = null } },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("routine-routine.developer").performClick()
        rule.onNodeWithText("Preview Developer").assertExists()
        rule.onNodeWithText("Install").performClick()
        rule.runOnIdle { assertEquals(ids, slots.value.mapNotNull { it?.id }) }
        rule.onNodeWithText("Undo").performClick()
        rule.runOnIdle { assertEquals(original, slots.value) }
    }

    @Test
    fun iconlessDecorTileHasNoGlyphPixelsOrIconSemanticsWhileFallbackStillRenders() {
        val showFallback = mutableStateOf(false)
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    DeckControlTile(
                        label = "Blank",
                        icon = if (showFallback.value) ActionIcon.Empty.imageVector() else null,
                        accentColor = Color(0xFF22D3EE),
                        onClick = {},
                        modifier = Modifier.size(120.dp).testTag("blank-decor-tile"),
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("deck-control-icon", useUnmergedTree = true).assertDoesNotExist()
        val blank = rule.onNodeWithTag("blank-decor-tile").captureToImage().toPixelMap()

        rule.runOnIdle { showFallback.value = true }
        rule.onNodeWithTag("deck-control-icon", useUnmergedTree = true).assertExists()
        val fallback = rule.onNodeWithTag("blank-decor-tile").captureToImage().toPixelMap()
        var changedPixels = 0
        for (y in 0 until minOf(blank.height, fallback.height)) {
            for (x in 0 until minOf(blank.width, fallback.width)) {
                if (blank[x, y] != fallback[x, y]) changedPixels += 1
            }
        }
        assertTrue("fallback glyph must change rendered pixels", changedPixels > 0)
    }

    @Test
    fun routineUndoRejectsSameIdWithMutatedContent() {
        fun action(id: String) = DeckAction(id, id.replace('_', ' '), ActionKind.Local, ActionIcon.Apps)
        val ids = listOf("coding_start", "terminal", "github", "dev_tools", "chatgpt", "screenshot", "copy", "paste")
        val catalog = ids.map(::action)
        val slots = mutableStateOf(List<DeckAction?>(8) { null })
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    RoutineBankPanel(
                        slots = slots.value,
                        allActions = catalog,
                        onAssign = { index, value -> slots.value = slots.value.toMutableList().also { it[index] = value } },
                        onRemove = { index -> slots.value = slots.value.toMutableList().also { it[index] = null } },
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("routine-routine.developer").performClick()
        rule.onNodeWithText("Install").performClick()
        rule.runOnIdle {
            slots.value = slots.value.toMutableList().also { values ->
                values[0] = requireNotNull(values[0]).copy(label = "mutated content")
            }
        }
        rule.onNodeWithText("Undo").performClick()
        rule.onNodeWithText("Undo unavailable: Deck changed after install.").assertExists()
        rule.runOnIdle { assertEquals("mutated content", slots.value[0]?.label) }
    }
}
