package io.codecks.core.common

@JvmInline
value class StableId(val value: String) {
    init {
        require(value.matches(ID_PATTERN)) { "StableId must be lowercase kebab/dot id: $value" }
    }

    override fun toString(): String = value

    private companion object {
        val ID_PATTERN = Regex("[a-z0-9]+([-.][a-z0-9]+)*")
    }
}

interface IdGenerator {
    fun next(prefix: String): StableId
}

