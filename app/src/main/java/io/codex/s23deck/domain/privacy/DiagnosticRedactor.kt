package io.codex.s23deck.domain.privacy

object DiagnosticRedactor {
    private val secretAssignment = Regex("""(?i)\b(password|token|key|secret|authorization)\b[^\s:=]*\s*[:=]\s*[^\s]+""")
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+""")
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val url = Regex("""\bhttps?://[^\s]+""")
    private val unixPath = Regex("""(?<!\w)/(?:Users|home|var|private|tmp)/[^\s]+""")

    fun redact(value: String, maxLength: Int = 240): String =
        value
            .replace(secretAssignment) { "${it.groupValues[1]}=<redacted>" }
            .replace(email, "<email>")
            .replace(url, "<url>")
            .replace(ipv4, "<ip>")
            .replace(unixPath, "<path>")
            .take(maxLength)
}
