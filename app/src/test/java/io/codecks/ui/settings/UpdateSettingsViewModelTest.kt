package io.codecks.ui.settings

import io.codecks.domain.update.ManualUpdateCheckRequest
import io.codecks.domain.update.SemanticVersion
import io.codecks.domain.update.UpdateAvailability
import io.codecks.domain.update.UpdateChecker
import io.codecks.domain.update.UpdateIntentPolicy
import io.codecks.domain.update.UpdateSourcePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val intentPolicy = UpdateIntentPolicy(
        UpdateSourcePolicy(
            releasesApiUrl = "https://api.github.com/repos/example/codecks/releases",
            apiHosts = setOf("api.github.com"),
            releasePageHosts = setOf("github.com"),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openingSettingsIsIdleAndDoesNotCheckAutomatically() {
        val checker = FakeChecker(Result.success(upToDate()))

        val viewModel = viewModel(checker)

        assertEquals(UpdateSettingsState.Idle, viewModel.state.value)
        assertEquals(0, checker.calls)
    }

    @Test
    fun explicitCheckPublishesAvailableAndBrowserFailureIsSafe() = runTest(dispatcher) {
        val checker = FakeChecker(
            Result.success(
                UpdateAvailability.Available(
                    currentVersion = version("1.0.0"),
                    latestVersion = version("1.1.0"),
                    releasePageUrl = "https://github.com/example/codecks/releases/tag/v1.1.0",
                ),
            ),
        )
        val viewModel = viewModel(checker)

        viewModel.checkForUpdate()
        assertEquals(UpdateSettingsState.Checking, viewModel.state.value)
        runCurrent()
        assertTrue(viewModel.state.value is UpdateSettingsState.Available)

        viewModel.openRelease { false }
        assertEquals(
            UpdateSettingsState.Failure(UpdateFailureKind.NoBrowser),
            viewModel.state.value,
        )
    }

    @Test
    fun failureDoesNotClaimCurrent() = runTest(dispatcher) {
        val viewModel = viewModel(FakeChecker(Result.failure(IllegalStateException("offline"))))

        viewModel.checkForUpdate()
        runCurrent()

        assertEquals(
            UpdateSettingsState.Failure(UpdateFailureKind.NetworkOrMetadata),
            viewModel.state.value,
        )
        assertFalse(viewModel.state.value is UpdateSettingsState.UpToDate)
    }

    private fun viewModel(checker: UpdateChecker) = UpdateViewModel(
        checker = checker,
        currentVersionName = "1.0.0",
        nowMillis = { 100L },
        appForeground = { true },
        intentPolicy = intentPolicy,
    )

    private fun upToDate() = UpdateAvailability.UpToDate(version("1.0.0"), version("1.0.0"))
    private fun version(value: String) = SemanticVersion.parse(value).getOrThrow()
}

private class FakeChecker(private val result: Result<UpdateAvailability>) : UpdateChecker {
    var calls = 0
    override suspend fun check(
        request: ManualUpdateCheckRequest,
        currentVersionName: String,
    ): Result<UpdateAvailability> {
        calls += 1
        return result
    }
}
