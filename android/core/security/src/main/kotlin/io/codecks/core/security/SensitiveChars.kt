package io.codecks.core.security

class SensitiveChars private constructor(
    private val chars: CharArray,
) : AutoCloseable {
    private var closed = false

    fun copyChars(): CharArray {
        check(!closed) { "Sensitive value is closed" }
        return chars.copyOf()
    }

    fun asStringForOneShotInterop(): String {
        check(!closed) { "Sensitive value is closed" }
        return String(chars)
    }

    override fun close() {
        chars.fill('\u0000')
        closed = true
    }

    override fun toString(): String = "SensitiveChars(REDACTED)"

    companion object {
        fun copyOf(chars: CharArray): SensitiveChars = SensitiveChars(chars.copyOf())

        fun copyOf(value: String): SensitiveChars = SensitiveChars(value.toCharArray())
    }
}

inline fun <T> SensitiveChars.useCopy(block: (CharArray) -> T): T {
    val copy = copyChars()
    return try {
        block(copy)
    } finally {
        copy.fill('\u0000')
    }
}
