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
