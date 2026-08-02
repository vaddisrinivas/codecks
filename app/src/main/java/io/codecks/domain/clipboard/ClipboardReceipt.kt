package io.codecks.domain.clipboard

enum class ClipboardDirection(val persistedCode: String) {
    PhoneToMac("phone_to_mac"),
    MacToPhone("mac_to_phone"),
    Bidirectional("bidirectional"),
}

enum class ClipboardTerminalResult(val persistedCode: String) {
    VerifiedSuccess("verified_success"),
    AppliedUnverified("applied_unverified"),
    Failure("failure"),
    Cancellation("cancellation"),
    Conflict("conflict"),
}

enum class ClipboardFailureCode(val persistedCode: String) {
    None("none"),
    MissingConnection("missing_connection"),
    MissingFingerprint("missing_fingerprint"),
    MissingSshKey("missing_ssh_key"),
    Timeout("timeout"),
    EmptyInput("empty_input"),
    VerificationMismatch("verification_mismatch"),
    Unknown("unknown"),
}

data class ClipboardReceipt(
    val direction: ClipboardDirection,
    val terminalResult: ClipboardTerminalResult,
    val failureCode: ClipboardFailureCode,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
) {
    init {
        require(startedAtMillis >= 0L)
        require(completedAtMillis >= startedAtMillis)
        require(
            (terminalResult == ClipboardTerminalResult.Failure) ==
                (failureCode != ClipboardFailureCode.None),
        ) { "Only failed clipboard receipts may carry a failure code" }
    }
}

class ClipboardOperation(
    private val direction: ClipboardDirection,
    private val startedAtMillis: Long,
    private val onTerminal: (ClipboardReceipt) -> Unit = {},
) {
    private var terminalReceipt: ClipboardReceipt? = null

    @Synchronized
    fun finish(
        terminalResult: ClipboardTerminalResult,
        failureCode: ClipboardFailureCode = ClipboardFailureCode.None,
        completedAtMillis: Long,
    ): ClipboardReceipt {
        terminalReceipt?.let { return it }
        return ClipboardReceipt(
            direction = direction,
            terminalResult = terminalResult,
            failureCode = failureCode,
            startedAtMillis = startedAtMillis,
            completedAtMillis = completedAtMillis,
        ).also {
            terminalReceipt = it
            onTerminal(it)
        }
    }
}
