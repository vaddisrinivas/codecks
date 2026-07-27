package io.codecks.core.trackpad

enum class LockscreenCapability {
    PointerMove,
    PointerScroll,
    MouseButton,
    HidShortcut,
    Keyboard,
    DeckAction,
    ReactiveAction,
    Clipboard,
    NotificationContent,
    Settings,
    Pairing,
    Reconnect,
    Disconnect,
}

enum class TrackpadEntryOrigin {
    ExactPublicUri,
    InternalWidget,
    InternalNotification,
    InternalApp,
    Unknown,
}

data class LockscreenControlState(
    val keyguardShowing: Boolean,
    val deviceLocked: Boolean,
    val userUnlockedSinceBoot: Boolean,
    val hidConnected: Boolean,
    val selectedHostPresent: Boolean,
    val bluetoothPermissionGranted: Boolean,
    val featureEnabled: Boolean,
    val entryOrigin: TrackpadEntryOrigin,
)

sealed interface LockscreenDecision {
    data object AllowRestrictedPointer : LockscreenDecision
    data object ForwardToUnlockedTrackpad : LockscreenDecision
    data object RequireUnlock : LockscreenDecision
    data object IgnoreAutomaticEntry : LockscreenDecision
}

object LockscreenTrackpadPolicy {
    fun decision(state: LockscreenControlState): LockscreenDecision =
        when {
            !state.keyguardShowing || !state.deviceLocked -> LockscreenDecision.ForwardToUnlockedTrackpad
            !state.userUnlockedSinceBoot -> LockscreenDecision.RequireUnlock
            state.entryOrigin == TrackpadEntryOrigin.Unknown -> LockscreenDecision.RequireUnlock
            !state.hidConnected && state.entryOrigin == TrackpadEntryOrigin.ExactPublicUri -> LockscreenDecision.IgnoreAutomaticEntry
            !state.hidConnected -> LockscreenDecision.RequireUnlock
            !state.selectedHostPresent -> LockscreenDecision.RequireUnlock
            !state.bluetoothPermissionGranted -> LockscreenDecision.RequireUnlock
            !state.featureEnabled -> LockscreenDecision.RequireUnlock
            else -> LockscreenDecision.AllowRestrictedPointer
        }

    fun allows(capability: LockscreenCapability, state: LockscreenControlState): Boolean =
        decision(state) == LockscreenDecision.AllowRestrictedPointer &&
            capability in setOf(
                LockscreenCapability.PointerMove,
                LockscreenCapability.PointerScroll,
                LockscreenCapability.MouseButton,
            )
}
