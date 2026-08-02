package io.codecks.domain.privacy

object DiagnosticRedactor {
    private val authorizationHeader = Regex("""(?i)\b(authorization|proxy-authorization)\s*:\s*(?:bearer|basic)?\s*[^\s,;]+""")
    private val cookieHeader = Regex("""(?i)\b(set-cookie|cookie)\s*:\s*[^\r\n]+""")
    private val bearerToken = Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]{8,}""")
    private val apiKeyHeader = Regex("""(?i)\b(x-api-key|api-key|anthropic-version)\s*:\s*[^\s,;]+""")
    private val knownSecretToken = Regex("""\b(?:sk-(?:proj-)?[A-Za-z0-9_-]{16,}|[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,})\b""")
    private val secretAssignment = Regex("""(?i)\b(password|passcode|token|api[_-]?key|secret|client[_-]?secret|access[_-]?token)\b[^\s:=]*\s*[:=]\s*(?:"[^"]*"|'[^']*'|[^\s,;]+)""")
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+""")
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val ipv6 = Regex("""(?i)(?<![A-F0-9:])(?:[A-F0-9]{0,4}:){2,7}[A-F0-9]{0,4}(?![A-F0-9:])""")
    private val url = Regex("""\bhttps?://[^\s]+""")
    private val unixPath = Regex("""(?<!\w)/(?:Users|home|var|private|tmp)/[^\s]+""")
    private val windowsPath = Regex("""(?i)\b[A-Z]:\\(?:Users|Documents and Settings|Windows|Temp)\\[^\s]+""")

    fun redact(value: String, maxLength: Int = 240): String =
        value
            .replace(authorizationHeader) { "${it.groupValues[1]}: <redacted>" }
            .replace(cookieHeader) { "${it.groupValues[1]}: <redacted>" }
            .replace(bearerToken, "Bearer <redacted>")
            .replace(apiKeyHeader) { "${it.groupValues[1]}: <redacted>" }
            .replace(knownSecretToken, "<redacted>")
            .replace(secretAssignment) { "${it.groupValues[1]}=<redacted>" }
            .replace(email, "<email>")
            .replace(url, "<url>")
            .replace(ipv4, "<ip>")
            .replace(ipv6, "<ip>")
            .replace(unixPath, "<path>")
            .replace(windowsPath, "<path>")
            .take(maxLength)
}
