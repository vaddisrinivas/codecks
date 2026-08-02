package io.codecks.ui.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibleStatusComposeInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun renderedStatusHasOneExplicitStateDescription() {
        compose.setContent {
            MaterialTheme {
                AccessibleStatus(
                    stateDescription = "Mac connected",
                    detail = "Verified now",
                    kind = AccessibleStatusKind.Success,
                )
            }
        }

        compose.onNode(hasStateDescription("Mac connected"))
            .assert(hasStateDescription("Mac connected"))
            .assert(hasContentDescription("Verified now"))
        compose.onAllNodes(hasStateDescription("Mac connected")).assertCountEquals(1)
    }

    @Test
    fun errorDetailIsExposedOnceThroughErrorSemantics() {
        compose.setContent {
            MaterialTheme {
                AccessibleStatus(
                    stateDescription = "Connection blocked",
                    detail = "Bluetooth permission required",
                    kind = AccessibleStatusKind.Error,
                )
            }
        }

        val error = SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Error,
            "Bluetooth permission required",
        )
        compose.onAllNodes(error).assertCountEquals(1)
        compose.onNode(error)
            .assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription))
    }
}
