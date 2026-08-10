package io.codecks.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val SSH_OUTPUT_LIMIT_BYTES = 64 * 1024

private fun ByteArrayOutputStream.writeBounded(buffer: ByteArray, count: Int) {
    if (size() >= SSH_OUTPUT_LIMIT_BYTES) return
    val allowed = (SSH_OUTPUT_LIMIT_BYTES - size()).coerceAtMost(count)
    if (allowed > 0) write(buffer, 0, allowed)
}

internal fun InputStream.readAvailableBounded(output: ByteArrayOutputStream, buffer: ByteArray) {
    while (available() > 0) {
        val count = read(buffer)
        if (count < 0) break
        output.writeBounded(buffer, count)
    }
}
