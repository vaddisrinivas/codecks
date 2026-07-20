package io.codecks.core.security

object Redactor {
    fun truncateUtf8(text: String, limitBytes: Int): RedactedText {
        require(limitBytes > 0) { "limitBytes must be positive" }
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= limitBytes) {
            return RedactedText(text = text, truncated = false)
        }
        return RedactedText(
            text = String(bytes.copyOf(limitBytes), Charsets.UTF_8).trimEnd('\uFFFD'),
            truncated = true,
        )
    }
}

data class RedactedText(
    val text: String,
    val truncated: Boolean,
)
