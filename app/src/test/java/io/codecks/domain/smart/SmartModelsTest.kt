package io.codecks.domain.smart

import org.junit.Assert.assertThrows
import org.junit.Test

class SmartModelsTest {
    @Test
    fun smartAppKeyRejectsBlankValue() {
        assertThrows(IllegalArgumentException::class.java) {
            SmartAppKey("")
        }
    }

    @Test
    fun smartMacIdRejectsBlankValue() {
        assertThrows(IllegalArgumentException::class.java) {
            SmartMacId("")
        }
    }
}
