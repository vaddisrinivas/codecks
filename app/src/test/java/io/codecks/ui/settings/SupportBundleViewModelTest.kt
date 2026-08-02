package io.codecks.ui.settings

import io.codecks.data.privacy.SupportBundleTempFilePolicy
import io.codecks.domain.privacy.SupportActionHealth
import io.codecks.domain.privacy.SupportBundleHealth
import io.codecks.domain.privacy.SupportBundleManifest
import io.codecks.domain.privacy.SupportBundleSettings
import io.codecks.domain.privacy.SupportBundleSnapshot
import io.codecks.domain.privacy.SupportConnectionHealth
import io.codecks.domain.privacy.SupportHidHealth
import io.codecks.domain.privacy.SupportIntervalBucket
import io.codecks.domain.privacy.SupportSpeedBucket
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SupportBundleViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun previewThenCancelCreatesNoFile() = runTest(dispatcher) {
        val root = createTempDirectory("support-preview-").toFile()
        try {
            val viewModel = SupportBundleViewModel(
                SupportBundleTempFilePolicy(root),
                nowEpochMs = { 1_000L },
                backgroundContext = dispatcher,
            )
            viewModel.preview()

            assertTrue(viewModel.state.value is SupportBundleUiState.Preview)
            viewModel.cancel()
            runCurrent()

            assertTrue(viewModel.state.value is SupportBundleUiState.Idle)
            assertFalse(root.walkTopDown().any { it.isFile })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun explicitGenerateCreatesReadyArchive() = runTest(dispatcher) {
        val root = createTempDirectory("support-generate-").toFile()
        try {
            val viewModel = SupportBundleViewModel(
                SupportBundleTempFilePolicy(root),
                nowEpochMs = { 2_000L },
                backgroundContext = dispatcher,
            )
            viewModel.preview()
            viewModel.generate(snapshot())
            runCurrent()

            val ready = viewModel.state.value as SupportBundleUiState.Ready
            assertTrue(ready.file.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun chooserOpenRetainsPendingFileUntilExplicitDelete() = runTest(dispatcher) {
        val root = createTempDirectory("support-pending-").toFile()
        try {
            val viewModel = SupportBundleViewModel(
                SupportBundleTempFilePolicy(root),
                nowEpochMs = { 3_000L },
                backgroundContext = dispatcher,
            )
            viewModel.preview()
            viewModel.generate(snapshot())
            runCurrent()
            val file = (viewModel.state.value as SupportBundleUiState.Ready).file

            viewModel.chooserOpened()
            assertTrue(viewModel.state.value is SupportBundleUiState.ChooserOpened)
            assertTrue(file.isFile)
            viewModel.shareTargetChosen()
            assertTrue((viewModel.state.value as SupportBundleUiState.ChooserOpened).targetChosen)
            viewModel.retryShare()
            assertTrue(viewModel.state.value is SupportBundleUiState.Ready)
            viewModel.chooserOpened()
            viewModel.deletePending()
            runCurrent()

            assertTrue(viewModel.state.value is SupportBundleUiState.Idle)
            assertFalse(file.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun processRestartRecoversPendingBundleForRetryOrDelete() = runTest(dispatcher) {
        val root = createTempDirectory("support-recover-").toFile()
        try {
            val policy = SupportBundleTempFilePolicy(root)
            val file = policy.write(byteArrayOf(1, 2, 3), 4_000L).getOrThrow()
            val viewModel = SupportBundleViewModel(
                policy,
                nowEpochMs = { 4_001L },
                backgroundContext = dispatcher,
            )

            runCurrent()

            val state = viewModel.state.value as SupportBundleUiState.PendingRetained
            assertTrue(state.file == file)
            assertTrue(file.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun generationCannotRacePastRecoveredPendingBundle() = runTest(dispatcher) {
        val root = createTempDirectory("support-recover-race-").toFile()
        try {
            val policy = SupportBundleTempFilePolicy(root)
            val existing = policy.write(byteArrayOf(1, 2, 3), 4_100L).getOrThrow()
            val viewModel = SupportBundleViewModel(
                policy,
                nowEpochMs = { 4_101L },
                backgroundContext = dispatcher,
            )

            viewModel.preview()
            viewModel.generate(snapshot())
            runCurrent()

            val state = viewModel.state.value as SupportBundleUiState.PendingRetained
            assertTrue(state.file == existing)
            assertTrue(root.walkTopDown().count { it.isFile && it.extension == "zip" } == 1)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun chooserDismissRetainsPendingBundle() {
        val file = java.io.File("pending.zip")
        assertFalse(
            supportBundleDismissDeletesPending(
                SupportBundleUiState.ChooserOpened(file),
            ),
        )
        assertTrue(supportBundleDismissDeletesPending(SupportBundleUiState.Preview()))
    }

    @Test
    fun deleteFailureConfirmationRetriesDeletion() {
        assertTrue(
            supportBundleConfirmAction(
                SupportBundleUiState.Failure(SupportBundleFailure.DELETE_FAILED),
            ) == SupportBundleConfirmAction.RetryDelete,
        )
        assertTrue(
            supportBundleConfirmAction(
                SupportBundleUiState.Failure(SupportBundleFailure.SHARE_PICKER_UNAVAILABLE),
            ) == SupportBundleConfirmAction.RetryShare,
        )
    }

    @Test
    fun pendingFailureDismissPolicyRetainsInsteadOfDeleting() {
        assertTrue(
            supportBundleDismissRetainsPending(
                SupportBundleUiState.Failure(SupportBundleFailure.SHARE_PICKER_UNAVAILABLE),
            ),
        )
        assertTrue(
            supportBundleDismissRetainsPending(
                SupportBundleUiState.Failure(SupportBundleFailure.DELETE_FAILED),
            ),
        )
        assertFalse(
            supportBundleDismissRetainsPending(
                SupportBundleUiState.Failure(SupportBundleFailure.BUILD_FAILED),
            ),
        )
    }

    @Test
    fun closingShareFailureRetainsGeneratedFile() = runTest(dispatcher) {
        val root = createTempDirectory("support-failure-close-").toFile()
        try {
            val viewModel = SupportBundleViewModel(
                SupportBundleTempFilePolicy(root),
                nowEpochMs = { 4_500L },
                backgroundContext = dispatcher,
            )
            viewModel.preview()
            viewModel.generate(snapshot())
            runCurrent()
            val file = (viewModel.state.value as SupportBundleUiState.Ready).file
            viewModel.shareFailed()

            viewModel.dismissRetaining()

            assertTrue(viewModel.state.value == SupportBundleUiState.PendingRetained(file))
            assertTrue(file.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun closingChooserRetainsFileWithoutTrappingModal() = runTest(dispatcher) {
        val root = createTempDirectory("support-close-").toFile()
        try {
            val policy = SupportBundleTempFilePolicy(root)
            val file = policy.write(byteArrayOf(1), 5_000L).getOrThrow()
            val viewModel = SupportBundleViewModel(
                policy,
                nowEpochMs = { 5_001L },
                backgroundContext = dispatcher,
            )
            runCurrent()
            viewModel.retryShare()
            viewModel.chooserOpened()
            viewModel.dismissRetaining()

            assertTrue(viewModel.state.value == SupportBundleUiState.PendingRetained(file))
            assertTrue(file.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deletingNewestPendingRediscoverOlderPending() = runTest(dispatcher) {
        val root = createTempDirectory("support-inventory-").toFile()
        try {
            val policy = SupportBundleTempFilePolicy(root)
            val older = policy.write(byteArrayOf(1), 6_000L).getOrThrow().apply { setLastModified(6_000L) }
            val newest = policy.write(byteArrayOf(2), 6_001L).getOrThrow().apply { setLastModified(6_001L) }
            val viewModel = SupportBundleViewModel(
                policy,
                nowEpochMs = { 6_002L },
                backgroundContext = dispatcher,
            )
            runCurrent()

            assertTrue((viewModel.state.value as SupportBundleUiState.PendingRetained).file == newest)
            viewModel.deletePending()
            runCurrent()

            assertFalse(newest.exists())
            assertTrue((viewModel.state.value as SupportBundleUiState.PendingRetained).file == older)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun snapshot() = SupportBundleSnapshot(
        manifest = SupportBundleManifest(appVersionCode = 1, debugBuild = true, createdAtEpochMs = 1L),
        health = SupportBundleHealth(
            connection = SupportConnectionHealth.UNCONFIGURED,
            sshKeyPresent = false,
            pinnedIdentityPresent = false,
            hid = SupportHidHealth.UNAVAILABLE,
            knownHostCount = 0,
            visibleActionCount = 0,
            catalogActionCount = 0,
            action = SupportActionHealth.IDLE,
            activityCount = 0,
            activityFailureCount = 0,
        ),
        events = emptyList(),
        settings = SupportBundleSettings(
            pointerSpeed = SupportSpeedBucket.MEDIUM,
            scrollRailEnabled = false,
            hapticsEnabled = false,
            pointerTraceEnabled = false,
            clipboardEnabled = false,
            clipboardInterval = SupportIntervalBucket.LONG,
            dynamicDeckEnabled = false,
            featureOverrideCount = 0,
            labsEnabled = false,
        ),
    )
}
