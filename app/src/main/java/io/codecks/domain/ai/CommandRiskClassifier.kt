package io.codecks.domain.ai

object CommandRiskClassifier {
    private val risks = listOf(
        Risk(Regex("""(?i)\b(rm|rmdir|unlink)\b"""), "deletes files"),
        Risk(Regex("""(?i)\b(kill|pkill|killall)\b"""), "stops a running process"),
        Risk(Regex("""(?i)\b(shutdown|reboot|halt)\b"""), "changes system availability"),
        Risk(Regex("""(?i)\b(curl|wget)\b.*(?:-d|--data|--upload-file|-T)\b"""), "sends data over the network"),
        Risk(Regex("""(?i)\b(mv|cp)\b|(?:^|[;&|]\s*)[^;&|]+\s*>\s*[^&|]"""), "moves, copies, or overwrites files"),
        Risk(Regex("""(?i)\bdefaults\s+write\b|\bchmod\b|\bchown\b"""), "changes system or file settings"),
    )

    fun reason(command: String): String? = risks.firstOrNull { it.pattern.containsMatchIn(command) }?.reason
}

private data class Risk(val pattern: Regex, val reason: String)
