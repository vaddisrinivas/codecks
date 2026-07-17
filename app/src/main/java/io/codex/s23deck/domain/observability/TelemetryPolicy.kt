package io.codex.s23deck.domain.observability

enum class TelemetryEventName(val wire: String) {
    AppStarted("app_started"),
    ConnectionSetup("connection_setup"),
    ActionRun("action_run"),
    AiDraft("ai_draft"),
    PurchaseVerification("purchase_verification"),
}

enum class TelemetryRoute(val wire: String) {
    App("app"),
    Deck("deck"),
    Trackpad("trackpad"),
    Automation("automation"),
    Ai("ai"),
    Settings("settings"),
    Backend("backend"),
}

enum class TelemetryResult(val wire: String) {
    Success("success"),
    Failed("failed"),
    Rejected("rejected"),
}

enum class TelemetryLatencyBucket(val wire: String) {
    Under250Ms("under_250_ms"),
    Under1S("under_1_s"),
    Under5S("under_5_s"),
    Over5S("over_5_s"),
}

enum class TelemetryErrorCode(val wire: String) {
    Network("network"),
    Unauthorized("unauthorized"),
    Quota("quota"),
    Validation("validation"),
    Unknown("unknown"),
}

data class TelemetryEvent(
    val name: TelemetryEventName,
    val route: TelemetryRoute = TelemetryRoute.App,
    val result: TelemetryResult,
    val latencyBucket: TelemetryLatencyBucket? = null,
    val errorCode: TelemetryErrorCode? = null,
)

object TelemetryPolicy {
    private val prohibitedFieldNames = setOf(
        "command",
        "stdout",
        "stderr",
        "clipboard",
        "notification",
        "prompt",
        "response",
        "token",
        "authorization",
        "api_key",
        "password",
        "hostname",
        "host",
        "email",
        "ip",
        "device_id",
        "purchase_token",
        "path",
    )

    fun acceptsCustomField(fieldName: String): Boolean =
        fieldName.lowercase().replace("-", "_") !in prohibitedFieldNames

    fun validateCustomFields(fieldNames: Iterable<String>): List<String> =
        fieldNames.filterNot(::acceptsCustomField)
}
