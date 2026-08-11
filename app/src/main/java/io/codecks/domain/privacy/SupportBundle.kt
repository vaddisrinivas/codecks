package io.codecks.domain.privacy

enum class SupportConnectionHealth { READY, CONFIGURED, UNCONFIGURED }
enum class SupportHidHealth { CONNECTED, READY, UNAVAILABLE }
enum class SupportActionHealth { IDLE, RUNNING, SUCCEEDED, FAILED }
enum class SupportSpeedBucket { LOW, MEDIUM, HIGH }
enum class SupportIntervalBucket { SHORT, MEDIUM, LONG }
enum class SupportPermissionState { GRANTED, MISSING, NOT_REQUIRED }
enum class SupportBatteryState { ACTIVE, INACTIVE }

data class SupportBundleManifest(
    val schemaVersion: Int = 1,
    val appVersionCode: Int,
    val debugBuild: Boolean,
    val createdAtEpochMs: Long,
)

data class SupportBundleHealth(
    val connection: SupportConnectionHealth,
    val sshKeyPresent: Boolean,
    val pinnedIdentityPresent: Boolean,
    val hid: SupportHidHealth,
    val knownHostCount: Int,
    val visibleActionCount: Int,
    val catalogActionCount: Int,
    val action: SupportActionHealth,
    val activityCount: Int,
    val activityFailureCount: Int,
)

data class SupportBundleSettings(
    val pointerSpeed: SupportSpeedBucket,
    val scrollRailEnabled: Boolean,
    val hapticsEnabled: Boolean,
    val pointerTraceEnabled: Boolean,
    val clipboardEnabled: Boolean,
    val clipboardInterval: SupportIntervalBucket,
    val dynamicDeckEnabled: Boolean,
    val featureOverrideCount: Int,
    val labsEnabled: Boolean,
)

data class SupportBundleRuntime(
    val bluetoothPermission: SupportPermissionState,
    val notificationPermission: SupportPermissionState,
    val batterySaver: SupportBatteryState,
    val backgroundRestricted: Boolean,
    val batteryOptimizationExempt: Boolean,
)

data class SupportOperationReceipt(
    val component: DiagnosticComponent,
    val result: DiagnosticResultCode,
    val attempt: Int,
    val durationMs: Long,
    val timestampEpochMs: Long,
) {
    init {
        require(attempt in 0..1_000)
        require(durationMs in 0L..24L * 60L * 60L * 1_000L)
        require(timestampEpochMs >= 0L)
    }
}

data class SupportBundleSnapshot(
    val manifest: SupportBundleManifest,
    val health: SupportBundleHealth,
    val events: List<DiagnosticEvent>,
    val receipts: List<SupportOperationReceipt>,
    val runtime: SupportBundleRuntime,
    val settings: SupportBundleSettings,
)
