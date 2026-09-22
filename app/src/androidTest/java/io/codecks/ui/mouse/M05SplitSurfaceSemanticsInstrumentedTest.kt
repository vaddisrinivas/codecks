package io.codecks.ui.mouse

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.codecks.MainActivity
import io.codecks.ui.theme.CodecksTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M05SplitSurfaceSemanticsInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun selectedTrackpadMenu_exposesButtonActionAndState() {
        var clicks = 0
        rule.activity.setContent {
            CodecksTheme {
                Box(Modifier.size(64.dp)) {
                    TrackpadMenuIcon(
                        icon = Icons.Outlined.Settings,
                        selected = true,
                        onClick = { clicks += 1 },
                        contentDescription = "Trackpad settings",
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("m05-trackpad-menu"),
                    )
                }
            }
        }

        val node = rule.onNodeWithTag("m05-trackpad-menu")
        val semantics = node.fetchSemanticsNode().config
        assertEquals(Role.Button, semantics[SemanticsProperties.Role])
        assertEquals("Selected", semantics[SemanticsProperties.StateDescription])
        assertEquals(listOf("Trackpad settings"), semantics[SemanticsProperties.ContentDescription])
        assertTrue(semantics.contains(SemanticsActions.OnClick))

        node.performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }
}
