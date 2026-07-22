package io.codecks.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.codecks.core.actions.ButtonState

object CodecksDesignTokens {
    object Spacing {
        val xxs = 2.dp
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 20.dp
        val xxl = 24.dp
        val page = 16.dp
    }

    object Size {
        val minTouchTarget = 48.dp
        val controlTileMinHeight = 88.dp
        val iconSm = 20.dp
        val iconMd = 24.dp
        val iconLg = 40.dp
        val sheetMaxWidth = 720.dp
    }

    object Stroke {
        val hairline = 1.dp
        val focus = 2.dp
    }

    object Motion {
        const val stateChangeMillis = 160
        const val screenTransitionMillis = 220
        const val longPressMillis = 420
    }
}

enum class CodecksHapticToken {
    Confirm,
    Reject,
    Boundary,
    LongPress,
}

@Composable
fun CodecksStateChip(
    label: String,
    state: ButtonState,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val container = when (state) {
        ButtonState.Running,
        ButtonState.Pressed -> colorScheme.primaryContainer
        ButtonState.Succeeded -> colorScheme.tertiaryContainer
        ButtonState.Failed -> colorScheme.errorContainer
        ButtonState.Disabled -> colorScheme.surfaceVariant
        ButtonState.Idle -> colorScheme.surfaceContainerLow
    }
    val content = when (state) {
        ButtonState.Failed -> colorScheme.onErrorContainer
        ButtonState.Disabled -> colorScheme.onSurfaceVariant
        else -> colorScheme.onSurface
    }
    Surface(
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun CodecksListRow(
    icon: ImageVector,
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
        trailingContent = value?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        modifier = modifier.heightIn(min = 64.dp),
    )
}

@Composable
fun CodecksIconLabel(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
