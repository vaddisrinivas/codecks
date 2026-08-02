package io.codecks.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

data class AccessibleActionContract(
    val minimumTargetDp: Int = 48,
    val role: String = "button",
)

fun accessibleActionContract(): AccessibleActionContract = AccessibleActionContract()

/**
 * Keeps the 48 dp interaction/semantics wrapper separate from the caller-owned visual surface.
 */
@Composable
fun AccessibleAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualModifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                contentDescription = label
                state?.let { stateDescription = it }
                if (!enabled) {
                    disabled()
                    stateDescription = state ?: "Disabled"
                }
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            ),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = visualModifier
                .wrapContentSize()
                .alpha(if (enabled) 1f else 0.38f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer,
                    content = content,
                )
            }
        }
    }
}
