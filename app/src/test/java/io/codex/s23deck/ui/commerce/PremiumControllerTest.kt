package io.codex.s23deck.ui.commerce

import io.codex.s23deck.domain.commerce.AccountState
import io.codex.s23deck.domain.commerce.BillingState
import io.codex.s23deck.domain.commerce.Entitlement
import io.codex.s23deck.domain.commerce.EntitlementStatus
import io.codex.s23deck.domain.commerce.EntitlementTier
import io.codex.s23deck.domain.commerce.FakeAccountRepository
import io.codex.s23deck.domain.commerce.FakeBillingRepository
import io.codex.s23deck.domain.commerce.FakeEntitlementRepository
import io.codex.s23deck.domain.commerce.FakeFeatureFlagRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PremiumControllerTest {
    @Test
    fun signedOutFreeStateShowsPaywallWithoutGrantingPremium() = runTest {
        val controller = controllerIn(this)
        runCurrent()

        val state = controller.uiState.value
        assertEquals(AccountState.SignedOut, state.accountState)
        assertFalse(state.hasPremium)
        assertTrue(state.products.isNotEmpty())
    }

    @Test
    fun signInAndPurchaseRefreshEntitlement() = runTest {
        val controller = controllerIn(
            scope = this,
            refreshEntitlement = Entitlement(EntitlementTier.Premium, EntitlementStatus.Active),
        )
        runCurrent()

        controller.signIn()
        runCurrent()
        controller.purchase("premium")
        runCurrent()

        assertTrue(controller.uiState.value.accountState is AccountState.SignedIn)
        assertTrue(controller.uiState.value.hasPremium)
        assertEquals("Premium active", controller.uiState.value.message)
    }

    @Test
    fun billingUnavailableExposesReason() = runTest {
        val controller = controllerIn(
            scope = this,
            billingState = BillingState.Unavailable("Play Billing unavailable"),
        )
        runCurrent()

        val state = controller.uiState.value
        assertTrue(state.products.isEmpty())
        assertEquals("Play Billing unavailable", state.message)
    }

    @Test
    fun deleteAccountSignsOutAndClearsPremiumState() = runTest {
        val accountRepository = FakeAccountRepository(
            AccountState.SignedIn(io.codex.s23deck.domain.commerce.Account("account-1", "user@example.com")),
        )
        val controller = PremiumController(
            accountRepository = accountRepository,
            billingRepository = FakeBillingRepository(),
            entitlementRepository = FakeEntitlementRepository(
                initialEntitlement = Entitlement(EntitlementTier.Premium, EntitlementStatus.Active),
            ),
            featureFlagRepository = FakeFeatureFlagRepository(),
            scope = TestScope(StandardTestDispatcher(testScheduler)),
        )
        runCurrent()

        controller.deleteAccount()
        runCurrent()

        assertEquals(1, accountRepository.deleteAccountCalls)
        assertEquals(AccountState.SignedOut, controller.uiState.value.accountState)
        assertEquals(EntitlementStatus.SignedOut, controller.uiState.value.entitlement.status)
        assertEquals("Account deleted", controller.uiState.value.message)
    }

    private fun controllerIn(
        scope: TestScope,
        billingState: BillingState = BillingState.Available,
        refreshEntitlement: Entitlement = Entitlement(EntitlementTier.Free, EntitlementStatus.Free),
    ): PremiumController =
        PremiumController(
            accountRepository = FakeAccountRepository(),
            billingRepository = FakeBillingRepository(initialState = billingState),
            entitlementRepository = FakeEntitlementRepository(
                initialEntitlement = Entitlement(EntitlementTier.Free, EntitlementStatus.Free),
                refreshResult = Result.success(refreshEntitlement),
            ),
            featureFlagRepository = FakeFeatureFlagRepository(),
            scope = TestScope(StandardTestDispatcher(scope.testScheduler)),
        )
}
