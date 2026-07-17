package io.codex.s23deck.ui.commerce

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.codex.s23deck.domain.ai.FeatureGate
import io.codex.s23deck.domain.commerce.AccountRepository
import io.codex.s23deck.domain.commerce.ActivityBoundCommerce
import io.codex.s23deck.domain.commerce.AccountState
import io.codex.s23deck.domain.commerce.BillingProduct
import io.codex.s23deck.domain.commerce.BillingRepository
import io.codex.s23deck.domain.commerce.BillingState
import io.codex.s23deck.domain.commerce.Entitlement
import io.codex.s23deck.domain.commerce.EntitlementRepository
import io.codex.s23deck.domain.commerce.EntitlementStatus
import io.codex.s23deck.domain.commerce.EntitlementTier
import io.codex.s23deck.domain.commerce.FeatureFlag
import io.codex.s23deck.domain.commerce.FeatureFlagRepository
import io.codex.s23deck.ui.designsystem.DeckActionButton
import io.codex.s23deck.ui.designsystem.DeckPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PremiumUiState(
    val accountState: AccountState = AccountState.SignedOut,
    val billingState: BillingState = BillingState.Available,
    val entitlement: Entitlement = Entitlement(EntitlementTier.Free, EntitlementStatus.Free),
    val products: List<BillingProduct> = emptyList(),
    val paywallEnabled: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val hasPremium: Boolean = entitlement.allows(FeatureGate.AiBuilder)
}

class PremiumController(
    private val accountRepository: AccountRepository,
    private val billingRepository: BillingRepository,
    private val entitlementRepository: EntitlementRepository,
    private val featureFlagRepository: FeatureFlagRepository,
    private val scope: CoroutineScope,
) {
    private val jobs = mutableListOf<Job>()
    private val _uiState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    init {
        jobs += scope.launch {
            accountRepository.account.collect { account ->
                _uiState.update { it.copy(accountState = account) }
            }
        }
        jobs += scope.launch {
            billingRepository.state.collect { billing ->
                _uiState.update { it.copy(billingState = billing) }
            }
        }
        jobs += scope.launch {
            entitlementRepository.entitlement.collect { entitlement ->
                _uiState.update { it.copy(entitlement = entitlement) }
            }
        }
        jobs += scope.launch {
            featureFlagRepository.flags.collect { flags ->
                _uiState.update { it.copy(paywallEnabled = flags[FeatureFlag.Paywall] != false) }
            }
        }
        loadProducts()
    }

    fun close() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    fun signIn() {
        scope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            accountRepository.signIn()
                .onSuccess { account ->
                    _uiState.update { it.copy(message = "Signed in as ${account.email}") }
                    refreshEntitlement()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "Sign-in failed") }
                }
            _uiState.update { it.copy(busy = false) }
        }
    }

    fun signOut() {
        scope.launch {
            accountRepository.signOut()
            _uiState.update { it.copy(message = "Signed out") }
        }
    }

    fun deleteAccount() {
        scope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            accountRepository.deleteAccount()
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            entitlement = Entitlement(EntitlementTier.Free, EntitlementStatus.SignedOut),
                            message = "Account deleted",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "Account deletion failed") }
                }
            _uiState.update { it.copy(busy = false) }
        }
    }

    fun loadProducts() {
        scope.launch {
            billingRepository.availableProducts()
                .onSuccess { products ->
                    _uiState.update { it.copy(products = products, message = null) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(products = emptyList(), message = error.message ?: "Billing unavailable") }
                }
        }
    }

    fun purchase(productId: String) {
        scope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            billingRepository.purchase(productId)
                .onSuccess { purchase ->
                    _uiState.update {
                        it.copy(message = if (purchase.verified) "Purchase verified" else "Purchase pending verification")
                    }
                    refreshEntitlement()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "Purchase failed") }
                }
            _uiState.update { it.copy(busy = false) }
        }
    }

    fun refreshEntitlement() {
        scope.launch {
            entitlementRepository.refresh()
                .onSuccess { entitlement ->
                    _uiState.update { it.copy(entitlement = entitlement, message = entitlement.status.label) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "Entitlement refresh failed") }
                }
        }
    }
}

@Composable
fun PremiumRoute(
    accountRepository: AccountRepository,
    billingRepository: BillingRepository,
    entitlementRepository: EntitlementRepository,
    featureFlagRepository: FeatureFlagRepository,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val controller = remember(accountRepository, billingRepository, entitlementRepository, featureFlagRepository) {
        PremiumController(accountRepository, billingRepository, entitlementRepository, featureFlagRepository, scope)
    }
    DisposableEffect(accountRepository, activity) {
        (accountRepository as? ActivityBoundCommerce)?.attachActivity(activity)
        onDispose { (accountRepository as? ActivityBoundCommerce)?.detachActivity(activity) }
    }
    DisposableEffect(billingRepository, activity) {
        (billingRepository as? ActivityBoundCommerce)?.attachActivity(activity)
        onDispose { (billingRepository as? ActivityBoundCommerce)?.detachActivity(activity) }
    }
    DisposableEffect(controller) { onDispose { controller.close() } }
    LaunchedEffect(controller) { controller.refreshEntitlement() }
    val state by controller.uiState.collectAsState()
    PremiumStatusScreen(
        state = state,
        contentPadding = contentPadding,
        onSignIn = controller::signIn,
        onSignOut = controller::signOut,
        onDeleteAccount = controller::deleteAccount,
        onPurchase = controller::purchase,
        onRefresh = controller::refreshEntitlement,
        modifier = modifier,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PremiumStatusScreen(
    state: PremiumUiState,
    contentPadding: PaddingValues,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onPurchase: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DeckPage(
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        item {
            PremiumHeader(
                state = state,
                onRefresh = onRefresh,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
        item {
            PaywallSurface(
                entitlement = state.entitlement,
                products = state.products,
                billingState = state.billingState,
                enabled = state.paywallEnabled && !state.busy,
                onPurchase = onPurchase,
                onRefresh = onRefresh,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            AccountSurface(
                accountState = state.accountState,
                busy = state.busy,
                onSignIn = onSignIn,
                onSignOut = onSignOut,
                onDeleteAccount = onDeleteAccount,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (state.message != null) {
            item {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PremiumHeader(
    state: PremiumUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.WorkspacePremium, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text("Account and Premium", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Sign in, manage Play Billing, and unlock AI Creator.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DeckActionButton(
                label = "Check",
                onClick = onRefresh,
                enabled = !state.busy,
                icon = Icons.Outlined.Refresh,
                modifier = Modifier.widthIn(min = 128.dp, max = 156.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
                label = state.entitlement.status.label,
                icon = { Icon(Icons.Outlined.WorkspacePremium, contentDescription = null) },
            )
            StatusPill(
                label = {
                    Text(
                        when (state.accountState) {
                            AccountState.SignedOut -> "Signed out"
                            is AccountState.SignedIn -> "Signed in"
                        },
                    )
                },
                icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    icon: @Composable () -> Unit,
) {
    StatusPill(label = { Text(label) }, icon = icon)
}

@Composable
private fun StatusPill(
    label: @Composable () -> Unit,
    icon: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            icon()
            label()
        }
    }
}

@Composable
fun PaywallSurface(
    entitlement: Entitlement,
    products: List<BillingProduct>,
    billingState: BillingState,
    onPurchase: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Creator Premium", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (entitlement.allows(FeatureGate.AiBuilder)) {
                            "Unlocked on this account."
                        } else {
                            "Create buttons, decks, and automations with AI and test connected model providers."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            EntitlementRow("AI-created buttons", entitlement.allows(FeatureGate.AiBuilder))
            EntitlementRow("AI-created decks", entitlement.allows(FeatureGate.AiBuilder))
            EntitlementRow("AI-created automations", entitlement.allows(FeatureGate.AiBuilder))
            EntitlementRow("Provider key testing", entitlement.allows(FeatureGate.AiBuilder))
            EntitlementRow("Play-backed entitlement sync", entitlement.status == EntitlementStatus.Active)
            when (billingState) {
                BillingState.Available -> {
                    if (products.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp),
                            ) {
                                Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("No active Play product found", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "Refresh after the Play product is available.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                DeckActionButton(
                                    label = "Refresh",
                                    onClick = onRefresh,
                                    icon = Icons.Outlined.Refresh,
                                    modifier = Modifier.widthIn(min = 128.dp, max = 148.dp),
                                )
                            }
                        }
                    } else {
                        products.forEach { product ->
                            DeckActionButton(
                                label = "Upgrade to ${product.title} - ${product.priceLabel}",
                                onClick = { onPurchase(product.id) },
                                enabled = enabled && !entitlement.allows(FeatureGate.AiBuilder),
                                icon = Icons.Outlined.ShoppingCart,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                is BillingState.Unavailable -> {
                    Text(
                        text = billingState.reason,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntitlementRow(
    label: String,
    active: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Icon(
            imageVector = if (active) Icons.Outlined.CheckCircle else Icons.Outlined.Lock,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                if (active) "Ready" else "Premium",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountSurface(
    accountState: AccountState,
    busy: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.AccountCircle, contentDescription = null)
                Column {
                    Text("Account", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when (accountState) {
                            AccountState.SignedOut -> "Signed out"
                            is AccountState.SignedIn -> accountState.account.email
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (accountState is AccountState.SignedIn) {
                DeckActionButton(
                    label = "Sign out",
                    onClick = onSignOut,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                DeckActionButton(
                    label = "Delete account",
                    onClick = { confirmDelete = true },
                    enabled = !busy,
                    icon = Icons.Outlined.DeleteOutline,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                DeckActionButton(
                    label = "Sign in",
                    onClick = onSignIn,
                    enabled = !busy,
                    icon = Icons.Outlined.AccountCircle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account") },
            text = {
                Text("This signs you out, revokes the Codecks server session, and removes local account state on this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteAccount()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private val EntitlementStatus.label: String
    get() = when (this) {
        EntitlementStatus.SignedOut -> "Signed out"
        EntitlementStatus.Free -> "Free plan"
        EntitlementStatus.Trialing -> "Premium trial"
        EntitlementStatus.Active -> "Premium active"
        EntitlementStatus.Expired -> "Premium expired"
        EntitlementStatus.Refunded -> "Purchase refunded"
        EntitlementStatus.StaleServer -> "Server check stale"
        EntitlementStatus.OfflineGrace -> "Offline grace"
    }
