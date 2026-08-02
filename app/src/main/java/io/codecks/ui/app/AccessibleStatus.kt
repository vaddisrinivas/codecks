package io.codecks.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

enum class AccessibleStatusKind {
    Neutral,
    Information,
    Success,
    Warning,
    Error,
    Busy,
}

data class AccessibleStatusSemantics(
    val stateDescription: String,
    val errorDescription: String?,
    val announcesPolitely: Boolean,
)

fun accessibleStatusSemantics(
    kind: AccessibleStatusKind,
    stateDescription: String,
    detail: String?,
    announceChanges: Boolean,
): AccessibleStatusSemantics = AccessibleStatusSemantics(
    stateDescription = stateDescription,
    errorDescription = detail?.takeIf { kind == AccessibleStatusKind.Error }
        ?: stateDescription.takeIf { kind == AccessibleStatusKind.Error },
    announcesPolitely = announceChanges,
)

/**
 * Shared status surface. Meaning is always present in text and semantics; color is supplementary.
 */
@Composable
fun AccessibleStatus(
    stateDescription: String,
    modifier: Modifier = Modifier,
    kind: AccessibleStatusKind = AccessibleStatusKind.Neutral,
    detail: String? = null,
    announceChanges: Boolean = false,
    announcementKey: String = "$kind:$stateDescription:${detail.orEmpty()}",
    role: Role = Role.Image,
) {
    val announcementGate = remember { TerminalAnnouncementGate() }
    val semanticState = accessibleStatusSemantics(
        kind = kind,
        stateDescription = stateDescription,
        detail = detail,
        announceChanges = announceChanges && announcementGate.consume(announcementKey),
    )
    val colors = accessibleStatusColors(kind)
    Surface(
        color = colors.container,
        contentColor = colors.content,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                this.role = role
                this.stateDescription = semanticState.stateDescription
                semanticState.errorDescription?.let { error(it) }
                if (semanticState.announcesPolitely) {
                    liveRegion = LiveRegionMode.Polite
                }
            },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Surface(
                color = colors.indicator,
                shape = CircleShape,
                modifier = Modifier.size(10.dp),
                content = {},
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stateDescription, style = MaterialTheme.typography.titleSmall)
                detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.content,
                    )
                }
            }
        }
    }
}

class TerminalAnnouncementGate {
    private var lastKey: String? = null

    fun consume(key: String): Boolean {
        if (key == lastKey) return false
        lastKey = key
        return true
    }
}

private data class AccessibleStatusColors(
    val container: Color,
    val content: Color,
    val indicator: Color,
)

@Composable
private fun accessibleStatusColors(kind: AccessibleStatusKind): AccessibleStatusColors {
    val scheme = MaterialTheme.colorScheme
    return when (kind) {
        AccessibleStatusKind.Neutral -> AccessibleStatusColors(
            container = scheme.surfaceContainerHigh,
            content = scheme.onSurface,
            indicator = scheme.outline,
        )
        AccessibleStatusKind.Information -> AccessibleStatusColors(
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
            indicator = scheme.primary,
        )
        AccessibleStatusKind.Success -> AccessibleStatusColors(
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer,
            indicator = scheme.tertiary,
        )
        AccessibleStatusKind.Warning -> AccessibleStatusColors(
            container = scheme.secondaryContainer,
            content = scheme.onSecondaryContainer,
            indicator = scheme.secondary,
        )
        AccessibleStatusKind.Error -> AccessibleStatusColors(
            container = scheme.errorContainer,
            content = scheme.onErrorContainer,
            indicator = scheme.error,
        )
        AccessibleStatusKind.Busy -> AccessibleStatusColors(
            container = scheme.surfaceContainerHigh,
            content = scheme.onSurface,
            indicator = scheme.primary,
        )
    }
}
