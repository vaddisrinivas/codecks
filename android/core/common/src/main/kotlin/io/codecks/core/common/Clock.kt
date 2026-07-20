package io.codecks.core.common

interface CodecksClock {
    fun nowEpochMillis(): Long
}

object SystemCodecksClock : CodecksClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}

