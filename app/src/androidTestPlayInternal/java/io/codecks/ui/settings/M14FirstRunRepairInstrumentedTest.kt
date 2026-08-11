package io.codecks.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import io.codecks.data.ConnectionConfig
import io.codecks.ui.connection.ConnectionUiState
import io.codecks.ui.designsystem.M09ADesignTestActivity
import io.codecks.ui.theme.CodecksTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class M14FirstRunRepairInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<M09ADesignTestActivity>()

    @Test
    fun authFailureOpensAndFocusesCredentialEditor() {
        render(error = "Permission denied (publickey)")
        rule.onNodeWithTag("mac-credential-repair-editor").performScrollTo().assertIsFocused()
    }

    @Test
    fun sleepingMacRetryInvokesTestCallback() {
        var retries = 0
        render(error = "Network unreachable", onTest = { retries += 1 })
        rule.onNodeWithTag("mac-repair-retry").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun changedIdentityRepairInvokesResetTrustCallback() {
        var resets = 0
        render(error = "REMOTE HOST IDENTIFICATION HAS CHANGED", onResetTrust = { resets += 1 })
        rule.onNodeWithTag("mac-repair-identity").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(0, resets) }
        rule.onNodeWithText("Review changed Mac identity").assertIsDisplayed()
        rule.onNodeWithTag("mac-repair-identity-confirm").performClick()
        rule.runOnIdle { assertEquals(1, resets) }
    }

    @Test
    fun directResetTrustRequiresConfirmation() {
        var resets = 0
        render(error = null, onResetTrust = { resets += 1 })
        rule.onNodeWithText("Advanced Mac controls").performScrollTo().performClick()
        rule.onNodeWithText("Reset trust").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        assertEquals(0, resets)
        rule.onNodeWithText("Review changed Mac identity").assertIsDisplayed()
        rule.onNodeWithTag("mac-repair-identity-confirm").performClick()
        rule.runOnIdle { assertEquals(1, resets) }
    }

    private fun render(
        error: String?,
        onTest: () -> Unit = {},
        onResetTrust: () -> Unit = {},
    ) {
        rule.setContent {
            CodecksTheme {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                    MacConnectionSettingsPanel(
                        state = ConnectionUiState(
                            config = ConnectionConfig(
                                host = "mac.local",
                                port = 22,
                                user = "codecks",
                                hasKey = true,
                                hostKey = "pinned-host-key",
                            ),
                            host = "mac.local",
                            port = "22",
                            user = "codecks",
                            error = error,
                        ),
                        onHostChange = {}, onPortChange = {}, onUserChange = {}, onPasswordChange = {},
                        onSelectHost = {}, onScan = {}, onScanLocalNetwork = {}, onVerifyHostKey = {},
                        onConfirmHostKey = {}, onAuthorize = {}, onRotateKey = {}, onResetTrust = onResetTrust,
                        onRemoveTarget = {}, onSavePassword = {}, onUseSavedPassword = {}, onTest = onTest,
                        onReactiveHelperPairingImport = {}, onOpenMacHelper = {},
                    )
                }
            }
        }
    }
}
