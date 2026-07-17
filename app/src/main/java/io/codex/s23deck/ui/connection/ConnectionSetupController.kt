package io.codex.s23deck.ui.connection

import android.app.Activity
import android.content.Context
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.NoCredentialException

class ConnectionSetupController(
    context: Context,
    private val activity: Activity?,
    private val viewModel: ConnectionViewModel,
) {
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(context)

    suspend fun savePassword() {
        val state = viewModel.uiState.value
        val id = viewModel.credentialId()
        if (state.host.isBlank() || state.user.isBlank() || state.password.isBlank()) {
            viewModel.setError("Enter Mac, username, and password first")
            return
        }
        runCatching {
            credentialManager.createCredential(
                context = activity ?: appContext,
                request = CreatePasswordRequest(
                    id = id,
                    password = state.password,
                ),
            )
        }.onSuccess {
            viewModel.setMessage("Saved to password manager")
        }.onFailure { error ->
            viewModel.setError(error.message ?: "Could not save password")
        }
    }

    suspend fun useSavedPassword() {
        val id = viewModel.credentialId()
        val state = viewModel.uiState.value
        if (state.host.isBlank() || state.user.isBlank()) {
            viewModel.setError("Enter Mac and username before using a saved password")
            return
        }
        runCatching {
            credentialManager.getCredential(
                context = activity ?: appContext,
                request = GetCredentialRequest(
                    credentialOptions = listOf(GetPasswordOption(setOf(id))),
                ),
            ).credential
        }.onSuccess { credential ->
            if (credential is PasswordCredential) {
                viewModel.applyPasswordCredential(credential.id, credential.password)
            } else {
                viewModel.setError("No password credential selected")
            }
        }.onFailure { error ->
            viewModel.setError(
                if (error is NoCredentialException) {
                    "No saved Mac password found"
                } else {
                    error.message ?: "Could not read saved password"
                },
            )
        }
    }
}
