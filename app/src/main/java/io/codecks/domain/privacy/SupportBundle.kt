package io.codecks.domain.privacy

enum class SupportConnectionHealth { READY, CONFIGURED, UNCONFIGURED }
enum class SupportHidHealth { CONNECTED, READY, UNAVAILABLE }
enum class SupportActionHealth { IDLE, RUNNING, SUCCEEDED, FAILED }
enum class SupportSpeedBucket { LOW, MEDIUM, HIGH }
enum class SupportIntervalBucket { SHORT, MEDIUM, LONG }

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

data class SupportBundleSnapshot(
    val manifest: SupportBundleManifest,
    val health: SupportBundleHealth,
    val events: List<DiagnosticEvent>,
    val settings: SupportBundleSettings,
)
