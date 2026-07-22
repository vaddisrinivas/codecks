package io.codecks.data.context

data class ContextFeatureStatus(
    val compiledIntoBuild: Boolean,
    val componentEnabled: Boolean,
    val specialAccessGranted: Boolean,
    val runtimeFeatureEnabled: Boolean,
    val privacyLaneEnabled: Boolean,
    val allowedPackageCount: Int,
) {
    val usable: Boolean
        get() = compiledIntoBuild &&
            componentEnabled &&
            specialAccessGranted &&
            runtimeFeatureEnabled &&
            privacyLaneEnabled &&
            allowedPackageCount > 0

    val label: String
        get() = if (usable) "Ready" else "Off"

    val summary: String
        get() = when {
            !compiledIntoBuild -> "Notification context is not compiled into this build"
            !componentEnabled -> "Notification listener component is disabled for this build"
            !runtimeFeatureEnabled -> "Context signals feature flag is off"
            !specialAccessGranted -> "Android notification access is not granted"
            !privacyLaneEnabled -> "Trackpad notification lane is off"
            allowedPackageCount == 0 -> "Choose apps before notification content can appear"
            else -> "Allowed apps can appear behind Trackpad with privacy filtering"
        }
}
