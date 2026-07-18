package io.codex.s23deck.domain.clipboard

data class ClipboardRisk(
    val label: String,
    val reason: String,
)

object ClipboardContentGuard {
    private val riskPatterns = listOf(
        Regex("""(?i)\b(password|passcode|otp|one[-_ ]?time code|2fa|mfa)\b""") to "possible password or one-time code",
        Regex("""(?i)\b(api[_-]?key|secret|token|bearer|private[_-]?key)\b""") to "possible token or secret",
        Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----""") to "private key block",
        Regex("""(?i)\bAKIA[0-9A-Z]{16}\b""") to "possible AWS access key",
        Regex("""(?i)\bsk-[A-Za-z0-9_-]{20,}\b""") to "possible API key",
    )

    fun riskFor(text: String): ClipboardRisk? {
        if (text.isBlank()) return null
        if (text.length > MAX_AUTOMATIC_SYNC_CHARS) {
            return ClipboardRisk("Large clipboard", "automatic sync skips very large clipboard text")
        }
        return riskPatterns.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }
            ?.let { (_, reason) -> ClipboardRisk("Sensitive-looking clipboard", reason) }
    }

    fun safePreview(text: String, maxChars: Int = 96): String {
        if (text.isBlank()) return "Empty"
        if (riskFor(text) != null) return "Hidden: sensitive-looking text"
        return text
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(maxChars)
            .ifBlank { "Whitespace only" }
    }

    const val MAX_AUTOMATIC_SYNC_CHARS = 12_000
}
