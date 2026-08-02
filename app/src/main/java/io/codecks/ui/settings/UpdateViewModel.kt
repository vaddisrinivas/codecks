package io.codecks.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.codecks.BuildConfig
import io.codecks.data.update.GitHubUpdateRepository
import io.codecks.domain.update.ManualUpdateCheckRequest
import io.codecks.domain.update.UpdateAvailability
import io.codecks.domain.update.UpdateChecker
import io.codecks.domain.update.UpdateIntentPolicy
import io.codecks.domain.update.UpdateSourcePolicy
import io.codecks.domain.privacy.DiagnosticResultCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class UpdateFailureKind {
    NetworkOrMetadata,
    BlockedReleaseUrl,
    NoBrowser,
}

sealed interface UpdateSettingsState {
    data object Idle : UpdateSettingsState
    data object Checking : UpdateSettingsState
    data class UpToDate(val checkedAtMillis: Long) : UpdateSettingsState
    data class Available(
        val releasePageUrl: String,
        val checkedAtMillis: Long,
    ) : UpdateSettingsState
    data class Failure(val kind: UpdateFailureKind) : UpdateSettingsState
}

class UpdateViewModel(
    checker: UpdateChecker? = null,
    private val currentVersionName: String = BuildConfig.VERSION_NAME,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val appForeground: () -> Boolean = { false },
    private val intentPolicy: UpdateIntentPolicy = UpdateIntentPolicy(
        UpdateSourcePolicy(
            releasesApiUrl = BuildConfig.GITHUB_RELEASES_API_URL,
            apiHosts = setOf(BuildConfig.GITHUB_API_HOST),
            releasePageHosts = setOf(BuildConfig.GITHUB_RELEASE_HOST),
        ),
    ),
    private val terminalEvent: (DiagnosticResultCode, Long) -> Unit = { _, _ -> },
) : ViewModel() {
    private val checker: UpdateChecker = checker ?: GitHubUpdateRepository(appForeground = appForeground)
    private val _state = MutableStateFlow<UpdateSettingsState>(UpdateSettingsState.Idle)
    val state: StateFlow<UpdateSettingsState> = _state.asStateFlow()

    fun checkForUpdate() {
        if (_state.value == UpdateSettingsState.Checking) return
        val request = ManualUpdateCheckRequest.fromExplicitForegroundAction(
            appForeground = appForeground(),
            requestedAtMillis = nowMillis(),
        ).getOrElse {
            _state.value = UpdateSettingsState.Failure(UpdateFailureKind.NetworkOrMetadata)
            return
        }
        _state.value = UpdateSettingsState.Checking
        viewModelScope.launch {
            checker.check(request, currentVersionName)
                .onSuccess { availability ->
                    val checkedAt = nowMillis()
                    terminalEvent(DiagnosticResultCode.SUCCEEDED, checkedAt)
                    _state.value = when (availability) {
                        is UpdateAvailability.UpToDate -> UpdateSettingsState.UpToDate(checkedAt)
                        is UpdateAvailability.Available -> {
                            val allowedUrl = intentPolicy.allowedExternalUrl(availability.releasePageUrl)
                            if (allowedUrl == null) {
                                UpdateSettingsState.Failure(UpdateFailureKind.BlockedReleaseUrl)
                            } else {
                                UpdateSettingsState.Available(allowedUrl, checkedAt)
                            }
                        }
                    }
                }
                .onFailure {
                    terminalEvent(DiagnosticResultCode.FAILED, nowMillis())
                    _state.value = UpdateSettingsState.Failure(UpdateFailureKind.NetworkOrMetadata)
                }
        }
    }

    fun openRelease(openExternalBrowser: (String) -> Boolean) {
        val available = _state.value as? UpdateSettingsState.Available ?: return
        val allowedUrl = intentPolicy.allowedExternalUrl(available.releasePageUrl)
        if (allowedUrl == null) {
            terminalEvent(DiagnosticResultCode.BLOCKED, nowMillis())
            _state.value = UpdateSettingsState.Failure(UpdateFailureKind.BlockedReleaseUrl)
        } else if (!openExternalBrowser(allowedUrl)) {
            terminalEvent(DiagnosticResultCode.FAILED, nowMillis())
            _state.value = UpdateSettingsState.Failure(UpdateFailureKind.NoBrowser)
        }
    }
}
