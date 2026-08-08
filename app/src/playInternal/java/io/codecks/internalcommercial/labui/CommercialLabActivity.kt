package io.codecks.internalcommercial.labui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class CommercialLabActivity : ComponentActivity() {
    private lateinit var machine: CommercialLabStateMachine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!CommercialLabLaunchPolicy.allows(packageName)) {
            finish()
            return
        }
        machine = runCatching {
            savedInstanceState?.toLabSavedState()?.let(CommercialLabStateMachine::restore)
        }.getOrNull() ?: CommercialLabStateMachine()
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CommercialLabRoute(machine)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::machine.isInitialized) outState.putLabSavedState(machine.savedState())
    }
}

private enum class LabPanel(val label: String) {
    ACCOUNT("Account"),
    SYNC("Backup"),
    BILLING("Billing"),
    PRIVACY("Privacy"),
}

@Composable
private fun CommercialLabRoute(machine: CommercialLabStateMachine) {
    var state by remember(machine) { mutableStateOf(machine.state) }
    var selectedPanelName by rememberSaveable { mutableStateOf(LabPanel.ACCOUNT.name) }
    val selectedPanel = enumValueOrDefault(selectedPanelName, LabPanel.ACCOUNT)

    fun dispatch(action: CommercialLabStateMachine.() -> Unit) {
        machine.action()
        state = machine.state
    }

    CommercialLabScreen(
        state = state,
        selectedPanel = selectedPanel,
        onSelectPanel = { selectedPanelName = it.name },
        onSignIn = { dispatch { signIn() } },
        onRecover = { dispatch { recoverSession() } },
        onSignOut = { dispatch { signOut() } },
        onDeleteRequest = { dispatch { requestAccountDeletion() } },
        onDeleteConfirmation = { dispatch { confirmAccountDeletion(it) } },
        onDeletionReauth = { dispatch { completeDeletionReauthentication(it) } },
        onUpload = { dispatch { uploadSnapshot() } },
        onPreview = { dispatch { downloadAndPreviewRestore() } },
        onRestore = { mode, fail -> dispatch { executeRestore(mode, fail) } },
        onPurchase = { dispatch { startPurchase() } },
        onVerify = { dispatch { verifyPurchase() } },
        onAcknowledge = { dispatch { acknowledgeAndRefreshEntitlement() } },
        onRestorePurchases = { dispatch { restorePurchases() } },
        onManage = { dispatch { openManagePurchases() } },
        onConsent = { dispatch { requestConsent() } },
        onPrivacyOptions = { dispatch { openPrivacyOptions() } },
        onAdRequest = { dispatch { requestApprovedDiscoveryAd() } },
    )
}

@Composable
private fun CommercialLabScreen(
    state: CommercialLabState,
    selectedPanel: LabPanel,
    onSelectPanel: (LabPanel) -> Unit,
    onSignIn: () -> Unit,
    onRecover: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteConfirmation: (Boolean) -> Unit,
    onDeletionReauth: (Boolean) -> Unit,
    onUpload: () -> Unit,
    onPreview: () -> Unit,
    onRestore: (LabRestoreMode, Boolean) -> Unit,
    onPurchase: () -> Unit,
    onVerify: () -> Unit,
    onAcknowledge: () -> Unit,
    onRestorePurchases: () -> Unit,
    onManage: () -> Unit,
    onConsent: () -> Unit,
    onPrivacyOptions: () -> Unit,
    onAdRequest: () -> Unit,
) {
    Scaffold { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).semantics { isTraversalGroup = true },
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TestBackendBanner()
            }
            item {
                LabStatusCard(state)
            }
            item {
                PrimaryScrollableTabRow(selectedTabIndex = selectedPanel.ordinal) {
                    LabPanel.entries.forEach { panel ->
                        Tab(
                            selected = selectedPanel == panel,
                            onClick = { onSelectPanel(panel) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            text = { Text(panel.label) },
                        )
                    }
                }
            }
            item {
                when (selectedPanel) {
                    LabPanel.ACCOUNT -> AccountPanel(
                        state,
                        onSignIn,
                        onRecover,
                        onSignOut,
                        onDeleteRequest,
                    )
                    LabPanel.SYNC -> SyncPanel(state, onUpload, onPreview, onRestore)
                    LabPanel.BILLING -> BillingPanel(
                        state,
                        onPurchase,
                        onVerify,
                        onAcknowledge,
                        onRestorePurchases,
                        onManage,
                    )
                    LabPanel.PRIVACY -> PrivacyPanel(state, onConsent, onPrivacyOptions, onAdRequest)
                }
            }
            item {
                Text(
                    "Receipts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
                )
            }
            if (state.receipts.isEmpty()) {
                item {
                    Text(
                        "No test actions yet.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.receipts.take(8), key = { it.sequence }) { receipt ->
                    ReceiptRow(receipt)
                }
            }
        }
    }

    if (state.deletion == LabDeletionState.CONFIRMATION_REQUIRED) {
        AlertDialog(
            onDismissRequest = { onDeleteConfirmation(false) },
            title = { Text("Delete internal test account?") },
            text = { Text("This simulation revokes sessions and removes cloud snapshots. Reauthentication is still required.") },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteConfirmation(true) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = "Confirm test account deletion"
                    },
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(
                    onClick = { onDeleteConfirmation(false) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Cancel") }
            },
        )
    }

    if (state.deletion == LabDeletionState.REAUTH_REQUIRED) {
        AlertDialog(
            onDismissRequest = { onDeletionReauth(false) },
            title = { Text("Reauthenticate") },
            text = { Text("Simulate a fresh Credential Manager check before deletion.") },
            confirmButton = {
                TextButton(
                    onClick = { onDeletionReauth(true) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = "Reauthenticate and delete test account"
                    },
                ) { Text("Reauthenticate and delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { onDeletionReauth(false) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Stop") }
            },
        )
    }
}

@Composable
private fun TestBackendBanner() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = "Internal test backend. No real SDK or network." },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("INTERNAL COMMERCIAL LAB", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Fake backend only · no real SDK, credentials, purchase, ad, or network traffic")
        }
    }
}

@Composable
private fun LabStatusCard(state: CommercialLabState) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = "Lab status. Session ${state.session}. Purchase ${state.purchase}. Restore ${state.restore}."
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Current state", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("Session: ${state.session.displayName()} · Consent: ${state.consent.displayName()}")
            Text("Purchase: ${state.purchase.displayName()} · Entitlement: ${if (state.entitlementActive) "active" else "none"}")
            Text("Deletion: ${state.deletion.displayName()}")
            Text(
                "Restore: ${state.restore.displayName()}" +
                    (state.lastRestoreMode?.let { " · Mode: ${it.displayName()}" } ?: ""),
            )
        }
    }
}

@Composable
private fun AccountPanel(
    state: CommercialLabState,
    onSignIn: () -> Unit,
    onRecover: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteRequest: () -> Unit,
) = LabCard("Account and deletion") {
    LabButton("Sign in with internal credential fake", "Simulate internal sign in", onSignIn)
    LabButton("Recover session", "Verify internal session recovery", onRecover)
    LabButton("Sign out", "Revoke and clear internal session", onSignOut)
    OutlinedButton(
        onClick = onDeleteRequest,
        enabled = state.session == LabSessionState.ACTIVE,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
            contentDescription = "Delete internal test account"
        },
    ) { Text("Delete test account") }
}

@Composable
private fun SyncPanel(
    state: CommercialLabState,
    onUpload: () -> Unit,
    onPreview: () -> Unit,
    onRestore: (LabRestoreMode, Boolean) -> Unit,
) = LabCard("Snapshot backup and restore") {
    LabButton("Upload safe snapshot", "Upload a safe internal snapshot", onUpload)
    LabButton("Download and preview", "Download and preview internal restore", onPreview)
    state.restorePreview?.let { preview ->
        Text(
            "Preview: ${preview.additions} additions · ${preview.replacements} replacements · " +
                "${preview.conflicts} conflict · ${preview.unsupported} unsupported",
        )
        Text("Checksum: ${preview.checksum.value.take(8)}…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Imported automations: disabled", color = MaterialTheme.colorScheme.primary)
    }
    LabButton("Merge restore", "Commit merge restore after safety backup") {
        onRestore(LabRestoreMode.MERGE, false)
    }
    LabButton("Replace restore", "Commit replace restore after safety backup") {
        onRestore(LabRestoreMode.REPLACE, false)
    }
    OutlinedButton(
        onClick = { onRestore(LabRestoreMode.MERGE, true) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
            contentDescription = "Simulate restore failure and rollback"
        },
    ) { Text("Simulate failure and rollback") }
}

@Composable
private fun BillingPanel(
    state: CommercialLabState,
    onPurchase: () -> Unit,
    onVerify: () -> Unit,
    onAcknowledge: () -> Unit,
    onRestorePurchases: () -> Unit,
    onManage: () -> Unit,
) = LabCard("Billing authority proof") {
    Text("Order is enforced: pending → server verify → acknowledge → entitlement refresh.")
    LabButton("Start pending purchase", "Start an internal pending purchase", onPurchase)
    LabButton("Verify with backend", "Verify pending purchase with internal backend", onVerify)
    LabButton("Acknowledge and refresh", "Acknowledge verified purchase and refresh entitlement", onAcknowledge)
    LabButton("Restore and reconcile", "Restore and reconcile internal purchases", onRestorePurchases)
    OutlinedButton(
        onClick = onManage,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text("Open manage-purchases fake") }
    Text("Current: ${state.purchase.displayName()}", color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun PrivacyPanel(
    state: CommercialLabState,
    onConsent: () -> Unit,
    onPrivacyOptions: () -> Unit,
    onAdRequest: () -> Unit,
) = LabCard("Consent and approved ad discovery") {
    LabButton("Request consent", "Request consent from internal privacy fake", onConsent)
    LabButton("Open privacy options", "Open internal privacy options", onPrivacyOptions)
    LabButton("Request routine-bank discovery demo", "Request approved discovery ad fake", onAdRequest)
    Text("Ads remain fake and limited to an approved discovery placement.")
    Text("Consent: ${state.consent.displayName()}", color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun LabCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

@Composable
private fun LabButton(label: String, description: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
            contentDescription = description
        },
    ) { Text(label) }
}

@Composable
private fun ReceiptRow(receipt: LabReceipt) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics(mergeDescendants = true) {
            contentDescription = "Receipt ${receipt.sequence}, ${receipt.kind}, ${receipt.summary}"
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("#${receipt.sequence} ${receipt.kind}", style = MaterialTheme.typography.labelLarge)
        }
        Text(receipt.summary, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

private fun Enum<*>.displayName(): String = name.lowercase().replace('_', ' ')

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default

private fun Bundle.putLabSavedState(saved: CommercialLabSavedState) {
    putString("lab.session", saved.session.name)
    putString("lab.deletion", saved.deletion.name)
    putString("lab.restore", saved.restore.name)
    putString("lab.checksum", saved.lastSnapshotChecksum?.value)
    putString("lab.restoreMode", saved.lastRestoreMode?.name)
    putString("lab.purchase", saved.purchase.name)
    putBoolean("lab.entitlement", saved.entitlementActive)
    putString("lab.consent", saved.consent.name)
    putInt("lab.nextReceipt", saved.nextReceiptSequence)
    saved.restorePreview?.let {
        putIntArray("lab.preview", intArrayOf(it.additions, it.replacements, it.conflicts, it.unsupported))
    }
    putIntegerArrayList("lab.receipt.sequence", ArrayList(saved.receipts.map(LabReceipt::sequence)))
    putStringArrayList("lab.receipt.code", ArrayList(saved.receipts.map { it.code.name }))
}

private fun Bundle.toLabSavedState(): CommercialLabSavedState? = runCatching {
    val receiptSequences = getIntegerArrayList("lab.receipt.sequence").orEmpty()
    val receiptCodes = getStringArrayList("lab.receipt.code").orEmpty()
    require(receiptSequences.size == receiptCodes.size)
    require(receiptSequences.size <= CommercialLabState.MAX_RECEIPTS)
    val receipts = receiptSequences.indices.map { index ->
        LabReceipt(receiptSequences[index], requireNotNull(enumValueOrNull<LabReceiptCode>(receiptCodes[index])))
    }
    val checksum = getString("lab.checksum")?.let(::LabSnapshotChecksum)
    val preview = getIntArray("lab.preview")?.takeIf { it.size == 4 }?.let {
        LabRestorePreview(it[0], it[1], it[2], it[3], requireNotNull(checksum))
    }
    CommercialLabSavedState(
        session = requireNotNull(enumValueOrNull<LabSessionState>(getString("lab.session"))),
        deletion = requireNotNull(enumValueOrNull<LabDeletionState>(getString("lab.deletion"))),
        restore = requireNotNull(enumValueOrNull<LabRestoreState>(getString("lab.restore"))),
        lastSnapshotChecksum = checksum,
        lastRestoreMode = getString("lab.restoreMode")?.let { requireNotNull(enumValueOrNull<LabRestoreMode>(it)) },
        purchase = requireNotNull(enumValueOrNull<LabPurchaseState>(getString("lab.purchase"))),
        entitlementActive = getBoolean("lab.entitlement", false),
        consent = requireNotNull(enumValueOrNull<LabConsentState>(getString("lab.consent"))),
        restorePreview = preview,
        receipts = receipts,
        nextReceiptSequence = getInt("lab.nextReceipt", 1).coerceAtLeast(1),
    )
}.getOrNull()

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
    enumValues<T>().firstOrNull { it.name == value }
