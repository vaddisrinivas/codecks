package io.codecks.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.codecks.domain.backup.RestorePlan
import io.codecks.domain.backup.RestoreSectionChangeKind
import io.codecks.ui.app.AccessibleStatus
import io.codecks.ui.app.AccessibleStatusKind
import io.codecks.ui.app.accessibilityTraversalOrder

@Composable
fun BackupRestoreDialog(
    plan: RestorePlan,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (plan.canConfirm) "Review restore" else "Backup cannot be restored") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (plan) {
                    is RestorePlan.Blocked -> {
                        AccessibleStatus(
                            stateDescription = "Restore blocked",
                            detail = "Reason: ${plan.reason.name}",
                            kind = AccessibleStatusKind.Error,
                            announceChanges = true,
                            announcementKey = "restore-blocked:${plan.reason.name}",
                            modifier = Modifier.accessibilityTraversalOrder(0f),
                        )
                        plan.warnings.forEach { Text(it) }
                    }
                    is RestorePlan.Ready -> {
                        AccessibleStatus(
                            stateDescription = "Restore preview ready",
                            detail = "No changes occur until you confirm this exact preview.",
                            kind = AccessibleStatusKind.Information,
                            announceChanges = true,
                            announcementKey = "restore-ready:${plan.planId}",
                            modifier = Modifier.accessibilityTraversalOrder(0f),
                        )
                        plan.sections.forEach { change ->
                            Text("${change.section}: ${change.kind.readableLabel()}")
                        }
                        plan.migrations.forEach { Text("Migration: $it") }
                        plan.warnings.forEach { Text("Warning: $it") }
                    }
                }
            }
        },
        confirmButton = {
            if (plan is RestorePlan.Ready) {
                TextButton(
                    onClick = { onConfirm(plan.planId) },
                    modifier = Modifier.accessibilityTraversalOrder(2f),
                ) {
                    Text("Confirm restore")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.accessibilityTraversalOrder(1f)) {
                Text("Cancel")
            }
        },
    )
}

private fun RestoreSectionChangeKind.readableLabel(): String = when (this) {
    RestoreSectionChangeKind.Added -> "will be added"
    RestoreSectionChangeKind.Replaced -> "will be replaced"
    RestoreSectionChangeKind.Removed -> "will be removed"
    RestoreSectionChangeKind.Unchanged -> "unchanged"
}
