package io.codecks.spikes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomAutomationHistorySpikeTest {
    @Test
    fun transactionalDaoContractCompiles() {
        val method = AutomationRunDao::class.java.getDeclaredMethod("replaceAtomically", AutomationRunRow::class.java, kotlin.coroutines.Continuation::class.java)
        assertTrue(method.name == "replaceAtomically")
    }

    @Test
    fun readerAndWriterPolicyFailsClosed() {
        assertTrue(AutomationHistoryPolicy.acceptsReader(1))
        assertTrue(AutomationHistoryPolicy.acceptsReader(2))
        assertFalse(AutomationHistoryPolicy.acceptsReader(0))
        assertFalse(AutomationHistoryPolicy.acceptsReader(3))
        assertFalse(AutomationHistoryPolicy.acceptsWriter(3))
    }

    @Test
    fun schemaHasNoSecretColumns() {
        val names = AutomationRunRow::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(names.none { name -> AutomationHistoryPolicy.forbiddenColumns.any(name::contains) })
    }
}
