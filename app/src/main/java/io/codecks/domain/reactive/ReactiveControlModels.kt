package io.codecks.domain.reactive

import java.security.MessageDigest

enum class ReactiveTrackpadMode {
    Pointer,
}

enum class ReactiveControlSource {
    FrontApp,
    ConnectionState,
    ClipboardState,
    MediaState,
    ShortcutCatalog,
    UndoReceipt,
}

enum class ReactiveIcon {
    ArrowLeft,
    ArrowRight,
    Reload,
    Add,
    Browser,
    Finder,
    Terminal,
    Generic,
}

enum class ReactiveRisk {
    Safe,
    Review,
    Dangerous,
    Private,
}

enum class ReactiveControlPolicy {
    Allow,
    RequiresReview,
    Deny,
}

enum class ReactiveStaleBehavior {
    Allow,
    Downgrade,
    Deny,
}

data class ReactiveControlConflict(
    val code: String,
    val otherControlId: ControlId? = null,
) {
    init {
        require(code.isNotBlank()) { "ReactiveControlConflict code must not be blank." }
    }
}

enum class SharedHidCommand {
    BrowserBack,
    BrowserForward,
    Reload,
    NewTab,
    Enter,
    CommandEnter,
    MediaPlayPause,
}

sealed interface ReactiveAction {
    data class ExistingCatalog(val actionId: String) : ReactiveAction {
        init {
            require(actionId.isNotBlank()) { "ReactiveAction.ExistingCatalog actionId must not be blank." }
        }
    }

    data class Hid(val command: SharedHidCommand) : ReactiveAction

    data class Helper(
        val actionId: String,
        val arguments: Map<String, String> = emptyMap(),
    ) : ReactiveAction {
        init {
            require(actionId.isNotBlank()) { "ReactiveAction.Helper actionId must not be blank." }
        }
    }

    data class BundledSshFallback(
        val bundleId: String,
        val arguments: Map<String, String> = emptyMap(),
    ) : ReactiveAction {
        init {
            require(bundleId.isNotBlank()) { "ReactiveAction.BundledSshFallback bundleId must not be blank." }
        }
    }

    data class Composite(val actions: List<ReactiveAction>) : ReactiveAction {
        init {
            require(actions.isNotEmpty()) { "ReactiveAction.Composite actions must not be empty." }
        }
    }

    data class ChangeMode(val mode: ReactiveTrackpadMode) : ReactiveAction
}

data class ReactiveControl(
    val id: ControlId,
    val providerId: String = "unknown",
    val actionId: String = "unknown",
    val title: String,
    val subtitle: String?,
    val icon: ReactiveIcon,
    val action: ReactiveAction,
    val source: ReactiveControlSource,
    val basePriority: Int,
    val confidence: Int = basePriority.coerceIn(0, 100),
    val reason: String,
    val explanation: String = reason,
    val policy: ReactiveControlPolicy = ReactiveControlPolicy.Allow,
    val conflicts: List<ReactiveControlConflict> = emptyList(),
    val requiredCapabilities: Set<CodecksCapability>,
    val risk: ReactiveRisk,
    val staleBehavior: ReactiveStaleBehavior = when (risk) {
        ReactiveRisk.Safe -> ReactiveStaleBehavior.Downgrade
        ReactiveRisk.Review,
        ReactiveRisk.Dangerous,
        ReactiveRisk.Private,
        -> ReactiveStaleBehavior.Deny
    },
    val reversible: Boolean,
    val stateRevision: Long,
    val actionRevision: ActionRevision,
    val expiresAtMillis: Long,
) {
    init {
        require(providerId.isNotBlank()) { "ReactiveControl providerId must not be blank." }
        require(actionId.isNotBlank()) { "ReactiveControl actionId must not be blank." }
        require(title.isNotBlank()) { "ReactiveControl title must not be blank." }
        require(confidence in 0..100) { "ReactiveControl confidence must be between 0 and 100." }
        require(reason.isNotBlank()) { "ReactiveControl reason must not be blank." }
        require(explanation.isNotBlank()) { "ReactiveControl explanation must not be blank." }
    }
}

data class ReactiveTrackpadContext(
    val mode: ReactiveTrackpadMode = ReactiveTrackpadMode.Pointer,
    val controlTtlMillis: Long = 3_000L,
    val hiddenControlIds: Set<ControlId> = emptySet(),
    val pinnedControlIds: List<ControlId> = emptyList(),
    val disabledProviderIds: Set<String> = emptySet(),
    val maxControls: Int? = null,
) {
    init {
        require(controlTtlMillis > 0) { "ReactiveTrackpadContext controlTtlMillis must be positive." }
        require(pinnedControlIds.distinct() == pinnedControlIds) {
            "ReactiveTrackpadContext pinnedControlIds must not contain duplicates."
        }
        require(disabledProviderIds.none { it.isBlank() }) {
            "ReactiveTrackpadContext disabledProviderIds must not contain blank ids."
        }
        require(maxControls == null || maxControls in 1..12) {
            "ReactiveTrackpadContext maxControls must be between 1 and 12."
        }
    }
}

interface ReactiveControlProvider {
    val id: String

    fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): List<ReactiveControl>
}

data class ReactiveCatalogControlSpec(
    val actionId: String,
    val title: String,
    val subtitle: String? = null,
    val icon: ReactiveIcon = ReactiveIcon.Generic,
    val requiredCapabilities: Set<CodecksCapability> = emptySet(),
    val risk: ReactiveRisk = ReactiveRisk.Safe,
    val reversible: Boolean = false,
    val resolvedActionRevision: ActionRevision? = null,
) {
    init {
        require(actionId.isNotBlank()) { "ReactiveCatalogControlSpec actionId must not be blank." }
        require(title.isNotBlank()) { "ReactiveCatalogControlSpec title must not be blank." }
    }

    fun actionRevision(): ActionRevision = resolvedActionRevision ?: ActionRevision(
        sha256Hex(
            listOf(
                actionId,
                title,
                subtitle.orEmpty(),
                icon.name,
                risk.name,
                reversible.toString(),
                requiredCapabilities.map { it.name }.sorted().joinToString(","),
            ).joinToString("|"),
        ),
    )
}

internal fun reactiveControlId(
    providerId: String,
    appIdentity: String,
    actionId: String,
): ControlId = ControlId("reactive-${sha256Hex("$providerId|$appIdentity|$actionId").take(32)}")

internal fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
