package io.codecks.transport.ssh

import java.io.ByteArrayOutputStream
import java.io.InputStream

data class CapturedStreams(
    val stdout: String,
    val stderr: String,
    val truncated: Boolean,
)

internal class OutputBudget(
    private val maxBytes: Int,
) {
    private var remaining = maxBytes
    var truncated: Boolean = false
        private set

    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
    }

    @Synchronized
    fun take(bytesAvailable: Int): Int {
        if (remaining <= 0) {
            truncated = true
            return 0
        }
        val allowed = minOf(bytesAvailable, remaining)
        remaining -= allowed
        if (allowed < bytesAvailable) {
            truncated = true
        }
        return allowed
    }
}

internal fun InputStream.readUtf8Capped(budget: OutputBudget): String {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        val allowed = budget.take(read)
        if (allowed > 0) {
            out.write(buffer, 0, allowed)
        }
    }
    return out.toString(Charsets.UTF_8.name())
}
