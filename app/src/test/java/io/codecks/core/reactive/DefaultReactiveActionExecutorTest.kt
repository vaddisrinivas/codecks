package io.codecks.core.reactive

import io.codecks.HidCommand
import io.codecks.HidHost
import io.codecks.HidLifecycle
import io.codecks.HidRepository
import io.codecks.HidState
import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.ActionSpec
import io.codecks.data.ActionRepository
import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.CommandOrigin
import io.codecks.domain.DeckAction
import io.codecks.domain.ExecutionAuthorization
import io.codecks.domain.deck.DeckLayout
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.device.DeviceId
import io.codecks.domain.reactive.ActionRevision
import io.codecks.domain.reactive.ControlId
import io.codecks.domain.reactive.InMemoryReactiveReceiptStore
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveActionInvocation
import io.codecks.domain.reactive.ReactiveActionResult
import io.codecks.domain.reactive.ReactiveAuthorization
import io.codecks.domain.reactive.ReactiveControl
import io.codecks.domain.reactive.ReactiveControlSource
import io.codecks.domain.reactive.ReactiveIcon
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.reactive.ReactiveIdempotencyKey
import io.codecks.domain.reactive.ReactiveOperationId
import io.codecks.domain.reactive.ReactiveRequestProvenance
import io.codecks.domain.reactive.ReactiveUndoOutcome
import io.codecks.domain.reactive.ReceiptId
import io.codecks.domain.reactive.SafeSftpTransferRequest
import io.codecks.domain.reactive.SftpAllowedRoots
import io.codecks.domain.reactive.SpotlightSearchRequest
import io.codecks.domain.reactive.SharedHidCommand
import io.codecks.domain.reactive.CapabilityAvailability
import io.codecks.domain.reactive.CapabilityState
import io.codecks.domain.reactive.MacAppKind
import io.codecks.domain.reactive.MacApplication
import io.codecks.domain.reactive.MacClipboardMetadata
import io.codecks.domain.reactive.MacClipboardKind
import io.codecks.domain.reactive.MacId
import io.codecks.domain.reactive.MacMeetingState
import io.codecks.domain.reactive.MacMediaState
import io.codecks.domain.reactive.MacSelection
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.MacSystemState
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.Observed
import io.codecks.domain.reactive.StateSource
import io.codecks.domain.reactive.CodecksCapability
import io.codecks.domain.reactive.TransferDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultReactiveActionExecutorTest {
    @Test
    fun hidSuccessStoresReceipt() = kotlinx.coroutines.test.runTest {
        val receipts = InMemoryReactiveReceiptStore()
        val hid = FakeHidRepository(isConnected = true)
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = hid,
            receiptStore = receipts,
        )

        val outcome = executor.execute(
            control = hidControl(),
            authorization = ReactiveAuthorization(),
            nowMillis = 10_000L,
        )

        assertEquals(ReactiveActionResult.Succeeded("hid_command_sent"), outcome.result)
        assertEquals(listOf(HidCommand.BrowserBack), hid.sentCommands)
        assertNotNull(outcome.receipt)
        assertEquals(1, receipts.all().size)
        assertEquals(1, receipts.protocolReceipts().size)
    }

    @Test
    fun hidDisconnectedFailsWithoutReceipt() = kotlinx.coroutines.test.runTest {
        val receipts = InMemoryReactiveReceiptStore()
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = FakeHidRepository(isConnected = false),
            receiptStore = receipts,
        )

        val outcome = executor.execute(
            control = hidControl(),
            authorization = ReactiveAuthorization(),
            nowMillis = 10_000L,
        )

        assertEquals(ReactiveActionResult.Failed("hid_not_connected", true), outcome.result)
        assertNull(outcome.receipt)
        assertTrue(receipts.all().isEmpty())
    }

    @Test
    fun replayingSameIdempotencyKeyReturnsStoredReceiptWithoutSendingAgain() = kotlinx.coroutines.test.runTest {
        val receipts = InMemoryReactiveReceiptStore()
        val hid = FakeHidRepository(isConnected = true)
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = hid,
            receiptStore = receipts,
        )
        val control = hidControl()
        val invocation = ReactiveActionInvocation(
            operationId = ReactiveOperationId("op-1"),
            idempotencyKey = ReactiveIdempotencyKey("idem-1"),
            requestedAtMillis = 10_000L,
        )

        val first = executor.execute(control, ReactiveAuthorization(), 10_001L, invocation = invocation)
        val replay = executor.execute(control, ReactiveAuthorization(), 10_500L, invocation = invocation)

        assertEquals(first.receipt?.id, replay.receipt?.id)
        assertEquals(listOf(HidCommand.BrowserBack), hid.sentCommands)
        assertEquals(1, receipts.all().size)
    }

    @Test
    fun reusingIdempotencyKeyForDifferentControlIsRejected() = kotlinx.coroutines.test.runTest {
        val receipts = InMemoryReactiveReceiptStore()
        val hid = FakeHidRepository(isConnected = true)
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = hid,
            receiptStore = receipts,
        )
        val invocation = ReactiveActionInvocation(
            operationId = ReactiveOperationId("op-1"),
            idempotencyKey = ReactiveIdempotencyKey("idem-1"),
            requestedAtMillis = 10_000L,
        )

        executor.execute(hidControl(SharedHidCommand.BrowserBack), ReactiveAuthorization(), 10_001L, invocation = invocation)
        val rejected = executor.execute(hidControl(SharedHidCommand.Reload), ReactiveAuthorization(), 10_002L, invocation = invocation)

        assertEquals(ReactiveActionResult.Failed("idempotency_key_reused", false), rejected.result)
        assertNull(rejected.receipt)
        assertEquals(listOf(HidCommand.BrowserBack), hid.sentCommands)
        assertEquals(1, receipts.all().size)
    }

    @Test
    fun timedOutInvocationExpiresBeforeSideEffect() = kotlinx.coroutines.test.runTest {
        val hid = FakeHidRepository(isConnected = true)
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = hid,
        )
        val invocation = ReactiveActionInvocation(
            operationId = ReactiveOperationId("op-timeout"),
            idempotencyKey = ReactiveIdempotencyKey("idem-timeout"),
            requestedAtMillis = 10_000L,
            timeoutMillis = 5L,
        )

        val outcome = executor.execute(hidControl(), ReactiveAuthorization(), 10_006L, invocation = invocation)

        assertEquals(ReactiveActionResult.Expired, outcome.result)
        assertNull(outcome.receipt)
        assertTrue(hid.sentCommands.isEmpty())
    }

    @Test
    fun spotlightPreviewRecordsReceiptWithoutRawQueryOrHidSideEffect() = kotlinx.coroutines.test.runTest {
        val receipts = InMemoryReactiveReceiptStore()
        val hid = FakeHidRepository(isConnected = true)
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = hid,
            receiptStore = receipts,
        )

        val outcome = executor.execute(
            control = reactiveControl(
                id = "reactive_spotlight",
                action = ReactiveAction.SpotlightPreview(
                    SpotlightSearchRequest(
                        query = "Quarterly Deck",
                        provenance = provenance(),
                    ),
                ),
                capability = CodecksCapability.SpotlightSearch,
            ),
            nowMillis = 10_000L,
        )

        assertEquals(ReactiveActionResult.Succeeded("spotlight_preview_recorded"), outcome.result)
        assertTrue(hid.sentCommands.isEmpty())
        val receipt = outcome.receipt!!
        assertEquals("spotlight_preview", receipt.metadata["operationKind"])
        assertEquals("8", receipt.metadata["maxResults"])
        assertNull(receipt.metadata["query"])
        assertTrue(receipt.metadata["queryFingerprint"]!!.length == 32)
    }

    @Test
    fun sftpTransferRequestReceiptStoresRootIdsAndFingerprintsOnly() = kotlinx.coroutines.test.runTest {
        val receipts = InMemoryReactiveReceiptStore()
        val sftp = FakeReactiveSftpTransferClient(ReactiveSftpTransferExecution.Succeeded)
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = FakeHidRepository(isConnected = true),
            receiptStore = receipts,
            sftpTransferClient = sftp,
        )
        val request = SafeSftpTransferRequest(
            direction = TransferDirection.MacToPhone,
            localPath = "/Users/me/Downloads/report.pdf",
            remotePath = "/phone/inbox/report.pdf",
            roots = SftpAllowedRoots(
                localRootId = "mac_downloads",
                localRoot = "/Users/me/Downloads",
                remoteRootId = "phone_inbox",
                remoteRoot = "/phone/inbox",
            ),
            provenance = provenance(),
        )

        val outcome = executor.execute(
            control = reactiveControl(
                id = "reactive_sftp",
                action = ReactiveAction.SftpTransferRequest(request),
                capability = CodecksCapability.SftpTransfer,
            ),
            nowMillis = 10_000L,
        )

        assertEquals(listOf(request), sftp.requests)
        assertEquals(ReactiveActionResult.Succeeded("sftp_transfer_completed"), outcome.result)
        val receipt = outcome.receipt!!
        assertEquals("sftp_transfer", receipt.metadata["operationKind"])
        assertEquals("mac_downloads", receipt.metadata["localRootId"])
        assertEquals("phone_inbox", receipt.metadata["remoteRootId"])
        assertNull(receipt.metadata["localPath"])
        assertNull(receipt.metadata["remotePath"])
        assertTrue(receipt.metadata["localPathFingerprint"]!!.length == 32)
    }

    @Test
    fun sftpTransferFailureDoesNotReportSuccess() = kotlinx.coroutines.test.runTest {
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = FakeHidRepository(isConnected = true),
            sftpTransferClient = FakeReactiveSftpTransferClient(
                ReactiveSftpTransferExecution.Failed("sftp_file_missing", retryable = false),
            ),
        )
        val request = SafeSftpTransferRequest(
            direction = TransferDirection.PhoneToMac,
            localPath = "/phone/outbox/report.pdf",
            remotePath = "/Users/me/Downloads/report.pdf",
            roots = SftpAllowedRoots(
                localRootId = "phone_outbox",
                localRoot = "/phone/outbox",
                remoteRootId = "mac_downloads",
                remoteRoot = "/Users/me/Downloads",
            ),
            provenance = provenance(),
        )

        val outcome = executor.execute(
            control = reactiveControl(
                id = "reactive_sftp_failure",
                action = ReactiveAction.SftpTransferRequest(request),
                capability = CodecksCapability.SftpTransfer,
            ),
            nowMillis = 10_000L,
        )

        assertEquals(ReactiveActionResult.Failed("sftp_file_missing", retryable = false), outcome.result)
        assertNull(outcome.receipt)
    }

    @Test
    fun helperSuccessCreatesReceiptAndSendsTypedExecuteRequest() = kotlinx.coroutines.test.runTest {
        val receipts = InMemoryReactiveReceiptStore()
        val helper = FakeReactiveHelperActionClient(
            next = ReactiveHelperActionExecution.Succeeded(
                resultCode = "shortcut_completed",
                helperReceiptId = "helper-receipt-1",
                completedAtMillis = 10_123L,
                undoToken = "undo-token-1",
            ),
        )
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = FakeHidRepository(isConnected = true),
            receiptStore = receipts,
            helperActionClient = helper,
        )

        val outcome = executor.execute(
            control = helperControl(),
            nowMillis = 10_000L,
            currentState = sampleState(
                capabilities = setOf(CapabilityState(CodecksCapability.MacCommand, CapabilityAvailability.Available)),
            ),
        )

        assertEquals(ReactiveActionResult.Succeeded("shortcut_completed"), outcome.result)
        assertNotNull(outcome.receipt)
        assertEquals(1, receipts.all().size)
        assertEquals("apple_shortcuts.run", helper.requests.single().actionId)
        assertEquals("Daily Standup", helper.requests.single().arguments["shortcutName"])
        assertTrue(helper.requests.single().preconditions.any { it.expectedBundleId == "com.apple.Terminal" })
        assertEquals("helper-receipt-1", outcome.receipt!!.metadata["helperReceiptId"])
    }

    @Test
    fun helperFailureDoesNotCreateReceipt() = kotlinx.coroutines.test.runTest {
        val receipts = InMemoryReactiveReceiptStore()
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = FakeHidRepository(isConnected = true),
            receiptStore = receipts,
            helperActionClient = FakeReactiveHelperActionClient(
                ReactiveHelperActionExecution.Failed("helper_failed", retryable = true),
            ),
        )

        val outcome = executor.execute(
            control = helperControl(),
            nowMillis = 10_000L,
            currentState = sampleState(
                capabilities = setOf(CapabilityState(CodecksCapability.MacCommand, CapabilityAvailability.Available)),
            ),
        )

        assertEquals(ReactiveActionResult.Failed("helper_failed", true), outcome.result)
        assertNull(outcome.receipt)
        assertTrue(receipts.all().isEmpty())
    }

    @Test
    fun helperReplayUsesStoredReceiptWithoutCallingHelperAgain() = kotlinx.coroutines.test.runTest {
        val receipts = InMemoryReactiveReceiptStore()
        val helper = FakeReactiveHelperActionClient(
            ReactiveHelperActionExecution.Succeeded(
                resultCode = "helper_ok",
                helperReceiptId = "helper-receipt-1",
                completedAtMillis = 10_001L,
            ),
        )
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = FakeHidRepository(isConnected = true),
            receiptStore = receipts,
            helperActionClient = helper,
        )
        val invocation = ReactiveActionInvocation(
            operationId = ReactiveOperationId("op-helper-1"),
            idempotencyKey = ReactiveIdempotencyKey("idem-helper-1"),
            requestedAtMillis = 10_000L,
        )
        val state = sampleState(
            capabilities = setOf(CapabilityState(CodecksCapability.MacCommand, CapabilityAvailability.Available)),
        )

        val first = executor.execute(helperControl(), nowMillis = 10_000L, currentState = state, invocation = invocation)
        val second = executor.execute(helperControl(), nowMillis = 10_002L, currentState = state, invocation = invocation)

        assertEquals(first.receipt?.id, second.receipt?.id)
        assertEquals(1, helper.requests.size)
        assertEquals(1, receipts.all().size)
    }

    @Test
    fun undoHidReceiptRunsInverseCommand() = kotlinx.coroutines.test.runTest {
        val receipts = InMemoryReactiveReceiptStore()
        val hid = FakeHidRepository(isConnected = true)
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = hid,
            receiptStore = receipts,
        )

        val outcome = executor.execute(hidControl(SharedHidCommand.BrowserBack), ReactiveAuthorization(), 10_000L)
        val undo = executor.undo(outcome.receipt!!.id, nowMillis = 10_001L)

        assertTrue(undo is ReactiveUndoOutcome.Succeeded)
        assertEquals(listOf(HidCommand.BrowserBack, HidCommand.BrowserForward), hid.sentCommands)
    }

    @Test
    fun expiredUndoDoesNotSendInverseCommand() = kotlinx.coroutines.test.runTest {
        val receipts = InMemoryReactiveReceiptStore()
        val hid = FakeHidRepository(isConnected = true)
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = hid,
            receiptStore = receipts,
        )

        val outcome = executor.execute(hidControl(SharedHidCommand.BrowserBack), ReactiveAuthorization(), 10_000L)
        val undo = executor.undo(outcome.receipt!!.id, nowMillis = 40_001L)

        assertEquals(ReactiveUndoOutcome.Expired, undo)
        assertEquals(listOf(HidCommand.BrowserBack), hid.sentCommands)
    }

    @Test
    fun reloadUsesMacCommandRInsteadOfCommandEnter() = kotlinx.coroutines.test.runTest {
        val hid = FakeHidRepository(isConnected = true)
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = hid,
        )

        val outcome = executor.execute(
            control = hidControl(SharedHidCommand.Reload),
            authorization = ReactiveAuthorization(),
            nowMillis = 10_000L,
        )

        assertEquals(ReactiveActionResult.Succeeded("hid_command_sent"), outcome.result)
        assertEquals(listOf(HidCommand.Reload), hid.sentCommands)
    }

    @Test
    fun dangerousCatalogRequiresConfirmation() = kotlinx.coroutines.test.runTest {
        val action = DeckAction(
            id = "lock",
            label = "Lock",
            kind = ActionKind.Ssh,
            icon = ActionIcon.Lock,
            dangerous = true,
            confirmationTitle = "Lock Mac",
            confirmationBody = "This locks the current Mac session.",
        )
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(listOf(action)),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = FakeHidRepository(isConnected = true),
        )

        val outcome = executor.execute(
            control = catalogControl(actionId = "lock", actionRevision = action.reactiveActionRevision()),
            authorization = ReactiveAuthorization(),
            nowMillis = 10_000L,
        )

        assertEquals(
            ReactiveActionResult.RequiresConfirmation(
                actionRevision = action.reactiveActionRevision(),
                title = "Lock Mac",
                body = "This locks the current Mac session.",
            ),
            outcome.result,
        )
        assertNull(outcome.receipt)
    }

    @Test
    fun reviewedCatalogCommandCanRun() = kotlinx.coroutines.test.runTest {
        val action = DeckAction(
            id = "echo",
            label = "Echo",
            kind = ActionKind.Ssh,
            icon = ActionIcon.Terminal,
            command = "echo hi",
            commandOrigin = CommandOrigin.UserAuthored,
            targetSelector = TargetSelector.CurrentDevice,
        )
        val runner = FakeReactiveActionRunner(
            next = ActionResult(
                actionId = "echo",
                title = "Echo",
                status = ActionResultStatus.Succeeded,
                message = "ok",
                timestampMillis = 12_345L,
            ),
        )
        val receipts = InMemoryReactiveReceiptStore()
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(listOf(action)),
            actionRunner = runner,
            hidRepository = FakeHidRepository(isConnected = true),
            receiptStore = receipts,
        )

        val outcome = executor.execute(
            control = catalogControl(actionId = "echo", actionRevision = action.reactiveActionRevision()),
            authorization = ReactiveAuthorization(reviewedActionRevision = action.reactiveActionRevision()),
            nowMillis = 10_000L,
        )

        assertEquals(ReactiveActionResult.Succeeded("catalog_action_succeeded"), outcome.result)
        assertNotNull(outcome.receipt)
        val spec = runner.lastSpec as ActionSpec.DeckActionSpec
        assertNotNull(spec.action.commandReview.reviewedRevision)
        assertEquals(1, receipts.all().size)
    }

    @Test
    fun catalogRequiresReviewWithoutAuthorization() = kotlinx.coroutines.test.runTest {
        val action = DeckAction(
            id = "echo",
            label = "Echo",
            kind = ActionKind.Ssh,
            icon = ActionIcon.Terminal,
            command = "echo hi",
            commandOrigin = CommandOrigin.UserAuthored,
        )
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(listOf(action)),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = FakeHidRepository(isConnected = true),
        )

        val outcome = executor.execute(
            control = catalogControl(actionId = "echo", actionRevision = action.reactiveActionRevision()),
            authorization = ReactiveAuthorization(),
            nowMillis = 10_000L,
        )

        assertTrue(outcome.result is ReactiveActionResult.RequiresReview)
        assertNull(outcome.receipt)
    }

    @Test
    fun changedCatalogActionCannotUsePreviouslyRenderedRevision() = kotlinx.coroutines.test.runTest {
        val first = DeckAction(
            id = "lock",
            label = "Lock",
            kind = ActionKind.Ssh,
            icon = ActionIcon.Lock,
            dangerous = true,
            command = "pmset displaysleepnow",
        )
        val replacement = first.copy(command = "shutdown -h now")
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(listOf(replacement)),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = FakeHidRepository(isConnected = true),
        )

        val outcome = executor.execute(
            control = catalogControl(actionId = first.id, actionRevision = first.reactiveActionRevision()),
            authorization = ReactiveAuthorization(confirmedActionRevision = first.reactiveActionRevision()),
            nowMillis = 10_000L,
        )

        assertEquals(ReactiveActionResult.Failed("stale_action_revision", false), outcome.result)
        assertNull(outcome.receipt)
    }

    @Test
    fun staleStateRevisionCannotExecuteCatalogAction() = kotlinx.coroutines.test.runTest {
        val action = safeCatalogAction()
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(listOf(action)),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = FakeHidRepository(isConnected = true),
        )

        val outcome = executor.execute(
            control = catalogControl(action.id, action.reactiveActionRevision()).copy(stateRevision = 1L),
            authorization = ReactiveAuthorization(),
            nowMillis = 10_000L,
            currentState = sampleState(snapshotRevision = 2L),
        )

        assertEquals(ReactiveActionResult.Failed("stale_state_revision", false), outcome.result)
        assertNull(outcome.receipt)
    }

    @Test
    fun unavailableRequiredCapabilityCannotExecuteCatalogAction() = kotlinx.coroutines.test.runTest {
        val action = safeCatalogAction()
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(listOf(action)),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = FakeHidRepository(isConnected = true),
        )

        val outcome = executor.execute(
            control = catalogControl(action.id, action.reactiveActionRevision()).copy(
                requiredCapabilities = setOf(CodecksCapability.MacCommand),
            ),
            authorization = ReactiveAuthorization(),
            nowMillis = 10_000L,
            currentState = sampleState(capabilities = emptySet()),
        )

        assertEquals(ReactiveActionResult.Unsupported("capability_unavailable"), outcome.result)
        assertNull(outcome.receipt)
    }

    @Test
    fun changedSpecificTargetCannotExecuteCatalogAction() = kotlinx.coroutines.test.runTest {
        val action = safeCatalogAction().copy(targetSelector = TargetSelector.SpecificDevice(DeviceId("other-mac")))
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(listOf(action)),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = FakeHidRepository(isConnected = true),
        )

        val outcome = executor.execute(
            control = catalogControl(action.id, action.reactiveActionRevision()),
            authorization = ReactiveAuthorization(),
            nowMillis = 10_000L,
            currentState = sampleState(),
        )

        assertEquals(ReactiveActionResult.Failed("target_changed", false), outcome.result)
        assertNull(outcome.receipt)
    }

    @Test
    fun repeatedInvocationReturnsStoredReceiptWithoutSendingAgain() = kotlinx.coroutines.test.runTest {
        val receipts = InMemoryReactiveReceiptStore()
        val hid = FakeHidRepository(isConnected = true)
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = hid,
            receiptStore = receipts,
        )
        val invocation = ReactiveActionInvocation(
            operationId = ReactiveOperationId("op-1"),
            idempotencyKey = ReactiveIdempotencyKey("same-key"),
            requestedAtMillis = 10_000L,
        )

        val first = executor.execute(hidControl(), nowMillis = 10_000L, invocation = invocation)
        val second = executor.execute(hidControl(), nowMillis = 10_001L, invocation = invocation)

        assertEquals(first.receipt?.id, second.receipt?.id)
        assertEquals(listOf(HidCommand.BrowserBack), hid.sentCommands)
        assertEquals(1, receipts.all().size)
    }

    @Test
    fun reusedIdempotencyKeyForDifferentControlIsDenied() = kotlinx.coroutines.test.runTest {
        val receipts = InMemoryReactiveReceiptStore()
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = FakeHidRepository(isConnected = true),
            receiptStore = receipts,
        )
        val invocation = ReactiveActionInvocation(
            operationId = ReactiveOperationId("op-2"),
            idempotencyKey = ReactiveIdempotencyKey("reused-key"),
            requestedAtMillis = 10_000L,
        )

        executor.execute(hidControl(), nowMillis = 10_000L, invocation = invocation)
        val reused = executor.execute(
            hidControl(SharedHidCommand.Reload).copy(id = ControlId("reactive_hid_reload")),
            nowMillis = 10_001L,
            invocation = invocation,
        )

        assertEquals(ReactiveActionResult.Failed("idempotency_key_reused", false), reused.result)
        assertNull(reused.receipt)
        assertEquals(1, receipts.all().size)
    }

    @Test
    fun undoMissingReceiptIsUnsupported() = kotlinx.coroutines.test.runTest {
        val executor = DefaultReactiveActionExecutor(
            actionRepository = FakeReactiveActionRepository(emptyList()),
            actionRunner = FakeReactiveActionRunner(),
            hidRepository = FakeHidRepository(isConnected = true),
        )

        val outcome = executor.undo(ReceiptId("missing"), nowMillis = 10_000L)

        assertEquals(ReactiveUndoOutcome.Unsupported("receipt_missing"), outcome)
    }

    private fun safeCatalogAction() = DeckAction(
        id = "echo",
        label = "Echo",
        kind = ActionKind.Ssh,
        icon = ActionIcon.Terminal,
        command = "echo hi",
        commandOrigin = CommandOrigin.Bundled,
        targetSelector = TargetSelector.CurrentDevice,
    )

    private fun sampleState(
        snapshotRevision: Long = 1L,
        capabilities: Set<CapabilityState> = setOf(
            CapabilityState(CodecksCapability.MacCommand, CapabilityAvailability.Available),
        ),
    ): MacStateSnapshot {
        fun <T> observed(value: T?): Observed<T> = Observed(
            value = value,
            status = if (value == null) ObservationStatus.Unavailable else ObservationStatus.Fresh,
            observedAtMillis = 9_000L,
            source = StateSource.Helper,
        )
        return MacStateSnapshot(
            macId = MacId("current-mac"),
            snapshotRevision = snapshotRevision,
            capturedAtMillis = 9_000L,
            frontApp = observed(MacApplication("com.apple.Terminal", "Terminal", MacAppKind.Terminal)),
            activeWindow = observed(null),
            displays = observed(emptyList()),
            cursor = observed(null),
            selection = observed(MacSelection.None),
            clipboard = observed<MacClipboardMetadata>(null),
            media = observed<MacMediaState>(null),
            system = observed<MacSystemState>(null),
            meeting = observed<MacMeetingState>(null),
            latestScreenshot = observed(null),
            capabilities = capabilities,
        )
    }

    private fun hidControl(command: SharedHidCommand = SharedHidCommand.BrowserBack): ReactiveControl = ReactiveControl(
        id = ControlId("reactive_hid_back"),
        title = "Back",
        subtitle = null,
        icon = ReactiveIcon.ArrowLeft,
        action = ReactiveAction.Hid(command),
        source = ReactiveControlSource.FrontApp,
        basePriority = 10,
        reason = "test",
        requiredCapabilities = emptySet(),
        risk = ReactiveRisk.Safe,
        reversible = false,
        stateRevision = 1L,
        actionRevision = ActionRevision("rev_hid"),
        expiresAtMillis = Long.MAX_VALUE,
    )

    private fun catalogControl(
        actionId: String,
        actionRevision: ActionRevision,
    ): ReactiveControl = ReactiveControl(
        id = ControlId("reactive_$actionId"),
        title = actionId,
        subtitle = null,
        icon = ReactiveIcon.Generic,
        action = ReactiveAction.ExistingCatalog(actionId),
        source = ReactiveControlSource.FrontApp,
        basePriority = 10,
        reason = "test",
        requiredCapabilities = emptySet(),
        risk = ReactiveRisk.Safe,
        reversible = false,
        stateRevision = 1L,
        actionRevision = actionRevision,
        expiresAtMillis = Long.MAX_VALUE,
    )

    private fun reactiveControl(
        id: String,
        action: ReactiveAction,
        capability: CodecksCapability,
    ): ReactiveControl = ReactiveControl(
        id = ControlId(id),
        title = id,
        subtitle = null,
        icon = ReactiveIcon.Generic,
        action = action,
        source = ReactiveControlSource.ConnectionState,
        basePriority = 10,
        reason = "test",
        requiredCapabilities = setOf(capability),
        risk = ReactiveRisk.Review,
        reversible = false,
        stateRevision = 1L,
        actionRevision = ActionRevision("rev_$id"),
        expiresAtMillis = Long.MAX_VALUE,
    )

    private fun helperControl(): ReactiveControl = reactiveControl(
        id = "reactive_helper_shortcut",
        action = ReactiveAction.Helper(
            actionId = "apple_shortcuts.run",
            arguments = mapOf("shortcutName" to "Daily Standup"),
        ),
        capability = CodecksCapability.MacCommand,
    )

    private fun provenance(): ReactiveRequestProvenance = ReactiveRequestProvenance(
        macId = MacId("123e4567-e89b-12d3-a456-426614174000"),
        snapshotRevision = 1L,
        source = StateSource.Helper,
        observedAtMillis = 9_000L,
    )
    @Test
    fun repositoryWithoutConfirmedTransportFailsClosed() = kotlinx.coroutines.test.runTest {
        val hid = FakeHidRepository(isConnected = true)

        val result = hid.deliver(HidCommand.Enter)

        assertTrue(result.isFailure)
        assertTrue(hid.sentCommands.isEmpty())
    }
}

private class FakeReactiveActionRunner(
    val next: ActionResult = ActionResult(
        actionId = "x",
        title = "X",
        status = ActionResultStatus.Succeeded,
        message = "ok",
        timestampMillis = 1_000L,
    ),
) : ActionRunner {
    var lastSpec: ActionSpec? = null
    var lastAuthorization: ExecutionAuthorization? = null

    override suspend fun run(spec: ActionSpec, authorization: ExecutionAuthorization): ActionResult {
        lastSpec = spec
        lastAuthorization = authorization
        return next.copy(actionId = spec.id, title = spec.title)
    }
}

private class FakeReactiveHelperActionClient(
    private val next: ReactiveHelperActionExecution,
) : ReactiveHelperActionClient {
    val requests = mutableListOf<io.codecks.shared.protocol.ReactiveHelperRequest.Execute>()

    override suspend fun execute(
        request: io.codecks.shared.protocol.ReactiveHelperRequest.Execute,
        deadlineMillis: Long,
    ): ReactiveHelperActionExecution {
        requests += request
        return next
    }
}

private class FakeReactiveSftpTransferClient(
    private val next: ReactiveSftpTransferExecution,
) : ReactiveSftpTransferClient {
    val requests = mutableListOf<SafeSftpTransferRequest>()

    override suspend fun transfer(request: SafeSftpTransferRequest): ReactiveSftpTransferExecution {
        requests += request
        return next
    }
}

private class FakeReactiveActionRepository(
    private val actions: List<DeckAction>,
) : ActionRepository {
    override fun favorites(): List<DeckAction> = actions
    override fun observeFavorites(): Flow<List<DeckAction>> = MutableStateFlow(actions)
    override fun layout(): DeckLayout = DeckLayout.fromActions(actions)
    override fun allActions(): List<DeckAction> = actions
    override suspend fun saveFavorites(actions: List<DeckAction>) = Unit
    override suspend fun exportLayout(): Result<String> = Result.success("")
    override suspend fun validateLayout(payload: String): Result<Unit> = Result.success(Unit)
    override suspend fun importLayout(payload: String): Result<Unit> = Result.success(Unit)
    override suspend fun run(action: DeckAction): Result<String> = Result.success("${action.label} ok")
    override suspend fun test(action: DeckAction): Result<String> = Result.success("${action.label} ok")
}

private class FakeHidRepository(
    isConnected: Boolean,
) : HidRepository {
    override val state = MutableStateFlow(
        HidState(
            status = if (isConnected) "Connected" else "Idle",
            lifecycle = if (isConnected) HidLifecycle.Connected else HidLifecycle.Idle,
            isReady = isConnected,
            isConnected = isConnected,
            hosts = listOf(HidHost("AA:BB", "Mac")),
            selectedHostAddress = "AA:BB",
        ),
    )
    val sentCommands = mutableListOf<HidCommand>()

    override fun start() = Unit
    override fun refreshHosts() = Unit
    override fun connect(address: String) = Unit
    override fun disconnect() = Unit
    override fun move(dx: Int, dy: Int) = Unit
    override fun scroll(vertical: Int, horizontal: Int) = Unit
    override fun click(buttonMask: Int) = Unit
    override fun press(buttonMask: Int) = Unit
    override fun releaseButtons() = Unit
    override fun typeText(text: String) = Unit
    override fun send(command: HidCommand) {
        sentCommands += command
    }
}
