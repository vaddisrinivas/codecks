package io.codecks.ui.connection

import io.codecks.domain.connection.ConnectionIssueCode
import org.junit.Assert.assertEquals
import org.junit.Test

class CodecksReadinessTest {
    @Test
    fun aggregatesTypedIssuesFromExistingHealthModels() {
        val readiness = codecksReadiness(
            connectionHealth = ConnectionHealth(
                kind = ConnectionHealthKind.Offline,
                title = "unused",
                detail = "unused",
            ),
            hidHealth = HidHealth(
                kind = HidHealthKind.PermissionMissing,
                title = "unused",
                detail = "unused",
            ),
            aiReady = false,
            setupCompletion = SetupCompletionEvaluation.REPAIR_REQUIRED(
                SetupRepairTarget.FindMac,
                "test",
            ),
        )

        assertEquals(
            setOf(
                ConnectionIssueCode.MAC_OFFLINE_OR_ASLEEP,
                ConnectionIssueCode.BLUETOOTH_PERMISSION_DENIED,
            ),
            readiness.issueCodes,
        )
    }
}
