package io.codecks

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.codecks.data.CodecksBackupRepository
import io.codecks.data.PendingBackupRecovery
import io.codecks.data.readCodecksBackupBounded
import io.codecks.domain.backup.BackupRestoreResult
import io.codecks.domain.backup.RestorePlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class BackupRuntime(
    val restorePlan: RestorePlan?,
    val pendingRecovery: PendingBackupRecovery?,
    val export: () -> Unit,
    val import: () -> Unit,
    val importRecovery: () -> Unit,
    val recover: () -> Unit,
    val cancelRestore: () -> Unit,
    val confirmRestore: (String) -> Unit,
)

@Composable
internal fun rememberBackupRuntime(
    appContext: Context,
    repository: CodecksBackupRepository,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
): BackupRuntime {
    var pendingExportPayload by remember { mutableStateOf<ByteArray?>(null) }
    var pendingRestorePayload by remember { mutableStateOf<ByteArray?>(null) }
    var restorePlan by remember { mutableStateOf<RestorePlan?>(null) }
    var pendingRecovery by remember { mutableStateOf<PendingBackupRecovery?>(null) }
    LaunchedEffect(Unit) {
        pendingRecovery = withContext(Dispatchers.IO) { repository.pendingRecovery() }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val payload = pendingExportPayload
        pendingExportPayload = null
        if (uri != null && payload != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri, "w")?.use { it.write(payload) }
                        ?: error("Could not open backup file")
                }
            }
            snackbarHostState.showSnackbar(
                result.fold(onSuccess = { "Codecks backup saved" }, onFailure = { it.message ?: "Backup failed" }),
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readCodecksBackupBounded() }
                        ?: error("Could not open backup file")
                } catch (error: Throwable) {
                    error.rethrowIfCancellationOrFatalForUi()
                    return@withContext Result.failure(error)
                }.let { bytes ->
                    try {
                        Result.success(bytes to repository.createRestorePlan(bytes).getOrThrow())
                    } catch (error: Throwable) {
                        error.rethrowIfCancellationOrFatalForUi()
                        Result.failure(error)
                    }
                }
            }
            result.onSuccess { (bytes, plan) ->
                pendingRestorePayload = bytes
                restorePlan = plan
            }.onFailure { snackbarHostState.showSnackbar(backupPreviewFailureMessage(it)) }
        }
    }
    val export: () -> Unit = {
        scope.launch {
            repository.exportArchive()
                .onSuccess { payload ->
                    pendingExportPayload = payload
                    exportLauncher.launch("codecks-backup-${System.currentTimeMillis()}.codecks.zip")
                }
                .onFailure { snackbarHostState.showSnackbar(it.message ?: "Backup failed") }
        }
    }
    val import: () -> Unit = {
        importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/json", "text/plain"))
    }
    val importRecovery: () -> Unit = {
        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
    }
    val recover: () -> Unit = recover@{
        val recovery = pendingRecovery ?: return@recover
        scope.launch {
            if (recovery is PendingBackupRecovery.Corrupt) {
                val choice = snackbarHostState.showSnackbar(
                    message = "Recovery data is unreadable. Quarantine it to allow future restores.",
                    actionLabel = "Quarantine",
                    withDismissAction = true,
                )
                if (choice == SnackbarResult.ActionPerformed) {
                    val result = withContext(Dispatchers.IO) {
                        repository.quarantineCorruptRecovery(recovery.recoveryId)
                    }
                    if (result.isSuccess) pendingRecovery = repository.pendingRecovery()
                    snackbarHostState.showSnackbar(
                        if (result.isSuccess) "Corrupt recovery quarantined" else "Could not quarantine recovery data",
                    )
                }
                return@launch
            }
            val result = withContext(Dispatchers.IO) { repository.recoverPending(recovery.recoveryId) }
            if (result.isSuccess) pendingRecovery = repository.pendingRecovery()
            snackbarHostState.showSnackbar(
                if (result.isSuccess) "Previous Deck and Rules recovered"
                else "Recovery failed; preserved data remains available",
            )
        }
    }
    val cancelRestore = {
        pendingRestorePayload = null
        restorePlan = null
    }
    val confirmRestore: (String) -> Unit = confirm@{ planId ->
        val bytes = pendingRestorePayload ?: return@confirm
        scope.launch {
            val result = withContext(Dispatchers.IO) { repository.restoreConfirmed(planId, bytes) }
            val outcome = result.getOrNull()
            if (outcome is BackupRestoreResult.Committed) {
                pendingRestorePayload = null
                restorePlan = null
            } else if (outcome is BackupRestoreResult.RecoveryRequired) {
                pendingRecovery = repository.pendingRecovery()
                pendingRestorePayload = null
                restorePlan = null
            }
            snackbarHostState.showSnackbar(
                when (outcome) {
                    is BackupRestoreResult.Committed -> "Deck and Rules restored"
                    is BackupRestoreResult.RolledBack -> "Restore failed; previous data restored"
                    is BackupRestoreResult.RecoveryRequired -> "Restore incomplete; recovery data preserved"
                    null -> "Restore preview expired; review again"
                },
            )
        }
    }
    return BackupRuntime(restorePlan, pendingRecovery, export, import, importRecovery, recover, cancelRestore, confirmRestore)
}
