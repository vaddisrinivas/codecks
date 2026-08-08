package io.codecks.domain.features

enum class FeatureVariant {
    PUBLIC_RELEASE,
    INTERNAL,
    DEBUG,
}

enum class FeatureValueType {
    BOOLEAN,
}

enum class FeatureFlagOwner {
    PRODUCT,
    LABS,
}

enum class FeatureExposure {
    USER_PREFERENCE,
    INTERNAL_ONLY,
}

enum class FeatureClass {
    CORE_PRODUCT,
    EXPERIMENT,
    LAB,
    MANDATORY_SECURITY,
}

enum class FeatureSafetyBehavior {
    LOCAL_ONLY,
    REMOTE_MAY_DISABLE,
}

data class FeatureFlagMetadata(
    val flag: FeatureFlag,
    val stableKey: String,
    val valueType: FeatureValueType,
    val owner: FeatureFlagOwner,
    val purpose: String,
    val defaults: Map<FeatureVariant, Boolean>,
    val minAppVersion: Int,
    val reviewExpiresAtEpochMillis: Long?,
    val exposure: FeatureExposure,
    val featureClass: FeatureClass,
    val safetyBehavior: FeatureSafetyBehavior,
) {
    init {
        require(stableKey.matches(Regex("[a-z][a-z0-9_.]{2,95}")))
        require(defaults.keys == FeatureVariant.entries.toSet())
        require(minAppVersion >= 1)
        require(reviewExpiresAtEpochMillis == null || reviewExpiresAtEpochMillis >= 0L)
        require(
            (safetyBehavior == FeatureSafetyBehavior.REMOTE_MAY_DISABLE) ==
                (featureClass == FeatureClass.EXPERIMENT || featureClass == FeatureClass.LAB),
        )
    }

    fun defaultFor(variant: FeatureVariant): Boolean = requireNotNull(defaults[variant])

    /** Remote config may only disable optional experiments and labs. */
    val remoteOperationalEligible: Boolean
        get() = safetyBehavior == FeatureSafetyBehavior.REMOTE_MAY_DISABLE
}

/**
 * Product/labs registry only. Commercial authority lives in domain/commercial and
 * is intentionally not a FeatureFlag or a SharedPreferences key.
 */
object FeatureFlagRegistry {
    val entries: List<FeatureFlagMetadata> = listOf(
        core(FeatureFlag.Deck, "product.deck", "Deck entry point"),
        core(FeatureFlag.Trackpad, "product.trackpad", "Trackpad entry point"),
        core(FeatureFlag.Automations, "product.automations", "Automation entry point"),
        core(FeatureFlag.Ai, "product.ai", "AI entry point"),
        core(FeatureFlag.Keyboard, "product.keyboard", "Keyboard entry point"),
        core(FeatureFlag.Clipboard, "product.clipboard", "Clipboard entry point"),
        core(FeatureFlag.Settings, "product.settings", "Settings entry point"),
        core(FeatureFlag.DeckEditor, "product.deck_editor", "Deck editing"),
        core(FeatureFlag.Connection, "product.connection", "Connection settings"),
        core(FeatureFlag.AiBuilder, "product.ai_builder", "AI builder"),
        experiment(FeatureFlag.SmartSuggestions, "experiment.smart_suggestions", "AI suggestions"),
        experiment(FeatureFlag.SmartDeck, "experiment.smart_deck", "AI deck suggestions"),
        experiment(FeatureFlag.SmartKeyboard, "experiment.smart_keyboard", "AI keyboard suggestions"),
        experiment(FeatureFlag.SmartClipboard, "experiment.smart_clipboard", "AI clipboard suggestions"),
        experiment(FeatureFlag.SmartRules, "experiment.smart_rules", "AI automation rules"),
        experiment(FeatureFlag.SmartSettings, "experiment.smart_settings", "AI settings suggestions"),
        experiment(FeatureFlag.SmartTrackpadSuggest, "experiment.smart_trackpad_suggest", "Trackpad suggestions"),
        experiment(FeatureFlag.SmartTrackpadSnap, "experiment.smart_trackpad_snap", "Trackpad snapping"),
        experiment(FeatureFlag.ReactiveTrackpad, "experiment.reactive_trackpad", "Reactive trackpad"),
        experiment(FeatureFlag.SmartOcr, "experiment.smart_ocr", "OCR suggestions"),
        lab(FeatureFlag.Labs, "lab.root", "Labs entry point"),
        lab(FeatureFlag.LabAirMouse, "lab.air_mouse", "Air mouse lab"),
        lab(FeatureFlag.LabAirTouch, "lab.air_touch", "Air touch lab"),
        lab(FeatureFlag.LabBackTap, "lab.back_tap", "Back-tap lab"),
        lab(FeatureFlag.LabVolumeKeys, "lab.volume_keys", "Volume-key lab"),
    )

    private val byFlag = entries.associateBy { it.flag }
    private val byStableKey = entries.associateBy { it.stableKey }

    init {
        check(byFlag.size == FeatureFlag.entries.size) { "Every product FeatureFlag needs typed metadata" }
        check(byStableKey.size == entries.size) { "Feature keys must stay unique" }
    }

    fun metadata(flag: FeatureFlag): FeatureFlagMetadata = requireNotNull(byFlag[flag])

    fun metadataForStableKey(stableKey: String): FeatureFlagMetadata? = byStableKey[stableKey]

    fun defaultFlags(variant: FeatureVariant): Map<FeatureFlag, Boolean> =
        entries.associate { it.flag to it.defaultFor(variant) }

    /** CI/release tripwire: temporary flags require an explicit review before their deadline. */
    fun expiredTemporaryFlags(nowEpochMillis: Long): List<FeatureFlagMetadata> =
        entries.filter { metadata ->
            metadata.reviewExpiresAtEpochMillis?.let { it <= nowEpochMillis } == true
        }

    val userPreferenceFlags: Set<FeatureFlag>
        get() = entries.filter { it.exposure == FeatureExposure.USER_PREFERENCE }.map { it.flag }.toSet()

    private fun core(flag: FeatureFlag, key: String, purpose: String) = metadata(
        flag = flag,
        stableKey = key,
        owner = FeatureFlagOwner.PRODUCT,
        purpose = purpose,
        featureClass = FeatureClass.CORE_PRODUCT,
        defaults = allVariants(enabled = true),
        reviewExpiry = null,
    )

    private fun experiment(flag: FeatureFlag, key: String, purpose: String) = metadata(
        flag = flag,
        stableKey = key,
        owner = FeatureFlagOwner.PRODUCT,
        purpose = purpose,
        featureClass = FeatureClass.EXPERIMENT,
        defaults = allVariants(enabled = false),
        reviewExpiry = REVIEW_BY_EPOCH_MILLIS,
    )

    private fun lab(flag: FeatureFlag, key: String, purpose: String) = metadata(
        flag = flag,
        stableKey = key,
        owner = FeatureFlagOwner.LABS,
        purpose = purpose,
        featureClass = FeatureClass.LAB,
        defaults = allVariants(enabled = false),
        reviewExpiry = REVIEW_BY_EPOCH_MILLIS,
    )

    private fun metadata(
        flag: FeatureFlag,
        stableKey: String,
        owner: FeatureFlagOwner,
        purpose: String,
        featureClass: FeatureClass,
        defaults: Map<FeatureVariant, Boolean>,
        reviewExpiry: Long?,
    ) = FeatureFlagMetadata(
        flag = flag,
        stableKey = stableKey,
        valueType = FeatureValueType.BOOLEAN,
        owner = owner,
        purpose = purpose,
        defaults = defaults,
        minAppVersion = 1,
        reviewExpiresAtEpochMillis = reviewExpiry,
        exposure = FeatureExposure.USER_PREFERENCE,
        featureClass = featureClass,
        safetyBehavior = if (featureClass == FeatureClass.CORE_PRODUCT) {
            FeatureSafetyBehavior.LOCAL_ONLY
        } else {
            FeatureSafetyBehavior.REMOTE_MAY_DISABLE
        },
    )

    private fun allVariants(enabled: Boolean): Map<FeatureVariant, Boolean> =
        FeatureVariant.entries.associateWith { enabled }

    private const val REVIEW_BY_EPOCH_MILLIS = 1_830_297_600_000L // 2028-01-01 UTC
}
