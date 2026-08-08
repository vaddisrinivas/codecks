package io.codecks.internalcommercial.labui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialLabStateMachineTest {
    @Test
    fun purchaseCannotGrantBeforeServerVerificationAndAcknowledgement() {
        val machine = CommercialLabStateMachine()

        machine.startPurchase()
        assertEquals(LabPurchaseState.IDLE, machine.state.purchase)
        assertFalse(machine.state.entitlementActive)

        machine.signIn()
        machine.startPurchase()
        assertEquals(LabPurchaseState.PENDING, machine.state.purchase)
        assertFalse(machine.state.entitlementActive)

        machine.verifyPurchase()
        assertEquals(LabPurchaseState.VERIFIED_UNACKNOWLEDGED, machine.state.purchase)
        assertFalse(machine.state.entitlementActive)

        machine.acknowledgeAndRefreshEntitlement()
        assertEquals(LabPurchaseState.ACTIVE, machine.state.purchase)
        assertTrue(machine.state.entitlementActive)
    }

    @Test
    fun deletionRequiresConfirmationAndFreshReauthentication() {
        val machine = CommercialLabStateMachine()
        machine.signIn()

        machine.requestAccountDeletion()
        assertEquals(LabDeletionState.CONFIRMATION_REQUIRED, machine.state.deletion)
        machine.confirmAccountDeletion(false)
        assertEquals(LabSessionState.ACTIVE, machine.state.session)

        machine.requestAccountDeletion()
        machine.confirmAccountDeletion(true)
        assertEquals(LabDeletionState.REAUTH_REQUIRED, machine.state.deletion)
        machine.completeDeletionReauthentication(false)
        assertEquals(LabSessionState.ACTIVE, machine.state.session)

        machine.completeDeletionReauthentication(true)
        assertEquals(LabSessionState.DELETED, machine.state.session)
        assertEquals(LabDeletionState.COMPLETE, machine.state.deletion)
        assertFalse(machine.state.entitlementActive)
        assertEquals(
            listOf(
                LabReceiptCode.DELETION_ACCOUNT_DELETED,
                LabReceiptCode.DELETION_SNAPSHOTS_REMOVED,
                LabReceiptCode.DELETION_SESSIONS_REVOKED,
            ),
            machine.state.receipts.take(3).map(LabReceipt::code),
        )

        machine.signOut()
        machine.signIn()
        assertEquals(LabSessionState.DELETED, machine.state.session)
    }

    @Test
    fun restoreAlwaysPreviewsDisablesAutomationsAndCanRollback() {
        val machine = CommercialLabStateMachine()
        machine.signIn()

        machine.executeRestore(LabRestoreMode.MERGE, simulateFailure = false)
        assertEquals(LabRestoreState.IDLE, machine.state.restore)

        machine.downloadAndPreviewRestore()
        assertEquals(LabRestoreState.PREVIEW_READY, machine.state.restore)
        assertFalse(machine.state.restorePreview!!.importedAutomationsEnabled)
        assertEquals(machine.state.lastSnapshotChecksum, machine.state.restorePreview!!.checksum)

        machine.executeRestore(LabRestoreMode.REPLACE, simulateFailure = true)
        assertEquals(LabRestoreState.ROLLED_BACK, machine.state.restore)
        assertEquals(LabRestoreMode.REPLACE, machine.state.lastRestoreMode)
        assertTrue(machine.state.receipts.first().summary.contains("safety backup rolled back"))
    }

    @Test
    fun approvedAdFakeRequiresSessionAndConsent() {
        val machine = CommercialLabStateMachine()
        machine.requestApprovedDiscoveryAd()
        assertTrue(machine.state.receipts.first().summary.startsWith("Ad denied"))

        machine.signIn()
        machine.requestConsent()
        machine.requestApprovedDiscoveryAd()
        assertEquals("Routine-bank discovery ad fake requested", machine.state.receipts.first().summary)
    }

    @Test
    fun savedStateRestoresTypedProcessWithoutSensitiveValues() {
        val machine = CommercialLabStateMachine()
        machine.signIn()
        machine.downloadAndPreviewRestore()
        machine.startPurchase()
        machine.verifyPurchase()

        val restored = CommercialLabStateMachine.restore(machine.savedState())

        assertEquals(machine.state.session, restored.state.session)
        assertEquals(machine.state.restore, restored.state.restore)
        assertEquals(machine.state.purchase, restored.state.purchase)
        assertEquals(machine.state.receipts, restored.state.receipts)
        val diagnostic = restored.state.toString().lowercase()
        listOf("token", "password", "clipboard", "ssh", "api key").forEach {
            assertFalse("diagnostic leaked $it", diagnostic.contains(it))
        }
    }

    @Test
    fun receiptsAreBounded() {
        val machine = CommercialLabStateMachine()
        repeat(CommercialLabState.MAX_RECEIPTS + 10) { machine.openPrivacyOptions() }

        assertEquals(CommercialLabState.MAX_RECEIPTS, machine.state.receipts.size)
        assertTrue(machine.state.receipts.zipWithNext().all { (a, b) -> a.sequence > b.sequence })
    }

    @Test
    fun pendingDeletionBlocksCommercialMutations() {
        val machine = CommercialLabStateMachine()
        machine.signIn()
        machine.requestAccountDeletion()

        machine.uploadSnapshot()
        machine.startPurchase()
        machine.requestConsent()

        assertEquals(LabDeletionState.CONFIRMATION_REQUIRED, machine.state.deletion)
        assertEquals(LabPurchaseState.IDLE, machine.state.purchase)
        assertEquals(LabConsentState.UNKNOWN, machine.state.consent)
        assertTrue(machine.state.receipts.take(3).all { it.code == LabReceiptCode.MUTATION_BLOCKED_BY_DELETION })
    }

    @Test
    fun invalidRestoredAuthorityAndReceiptOrderingFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            CommercialLabState(
                session = LabSessionState.SIGNED_OUT,
                purchase = LabPurchaseState.ACTIVE,
                entitlementActive = true,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommercialLabState(
                receipts = listOf(
                    LabReceipt(1, LabReceiptCode.PRIVACY_OPTIONS_OPENED),
                    LabReceipt(2, LabReceiptCode.CONSENT_GRANTED),
                ),
                nextReceiptSequence = 3,
            )
        }
    }
}
