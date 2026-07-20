package io.codecks.core.testing

import io.codecks.core.common.CodecksClock
import io.codecks.core.common.IdGenerator
import io.codecks.core.common.StableId

class FixedClock(private var current: Long = 1_800_000_000_000L) : CodecksClock {
    override fun nowEpochMillis(): Long = current

    fun advanceBy(millis: Long) {
        current += millis
    }
}

class CountingIdGenerator : IdGenerator {
    private var counter = 0

    override fun next(prefix: String): StableId {
        counter += 1
        return StableId("$prefix-$counter")
    }
}

