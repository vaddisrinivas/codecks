package io.codecks.ui.connection

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupSnapshotPersistenceTest {
    private val now = System.currentTimeMillis()

    @Test
    fun resumesAtFirstMandatoryStepWithoutValidPassReceipt() {
        val snapshot = SetupSnapshot(
            receipts = mapOf(
                SetupStep.FindMac to receipt(SetupStep.FindMac, SetupReceiptResult.Passed, now - 40L),
                SetupStep.TrustMac to receipt(SetupStep.TrustMac, SetupReceiptResult.Skipped, now - 30L),
                SetupStep.Authorize to receipt(SetupStep.Authorize, SetupReceiptResult.Failed, now - 20L),
                SetupStep.VerifyControls to receipt(SetupStep.VerifyControls, SetupReceiptResult.Passed, now - 10L),
            ),
        )

        assertTrue(snapshot.isPassed(SetupStep.FindMac))
        assertFalse(snapshot.isPassed(SetupStep.TrustMac))
        assertFalse(snapshot.isPassed(SetupStep.Authorize))
        assertEquals(SetupStep.TrustMac, snapshot.firstMandatoryNotPassed)
        assertFalse(snapshot.isComplete)
    }

    @Test
    fun safeCodecRoundTripPersistsOnlyReceiptMetadata() {
        val complete = SetupSnapshot(
            receipts = SetupStep.entries.associateWith { step ->
                receipt(step, SetupReceiptResult.Passed, now - 1_000L + step.ordinal)
            },
        )
        val encoded = SetupSnapshotCodec.encode(complete)
        val restored = SetupSnapshotCodec.decode(encoded)

        assertTrue(restored.isComplete)
        assertNull(restored.firstMandatoryNotPassed)
        forbiddenTokens.forEach { token ->
            assertFalse(encoded.contains(token, ignoreCase = true))
        }
    }

    @Test
    fun corruptOrUnknownSnapshotFailsClosed() {
        assertEquals(SetupStep.FindMac, SetupSnapshotCodec.decode("99|find_mac,passed,1").firstMandatoryNotPassed)
        assertEquals(SetupStep.FindMac, SetupSnapshotCodec.decode("1|find_mac,passed,-1").firstMandatoryNotPassed)
        assertEquals(SetupStep.FindMac, SetupSnapshotCodec.decode("1|unknown,passed,1").firstMandatoryNotPassed)
    }

    @Test
    fun staleAndFutureReceiptsFailClosed() {
        val stale = receipt(
            SetupStep.FindMac,
            SetupReceiptResult.Passed,
            now - SETUP_RECEIPT_MAX_AGE_MS - 1L,
        )
        val future = receipt(SetupStep.FindMac, SetupReceiptResult.Passed, now + 5L * 60L * 1_000L + 1L)

        assertFalse(stale.isValidPassFor(SetupStep.FindMac, now))
        assertFalse(future.isValidPassFor(SetupStep.FindMac, now))
    }

    @Test
    fun sourceUsesExistingFlowAndAppPrivateSettings() {
        val store = File("src/main/java/io/codecks/ui/connection/SetupSnapshot.kt").readText()
        val viewModel = File("src/main/java/io/codecks/ui/connection/ConnectionViewModel.kt").readText()
        val controller = File("src/main/java/io/codecks/ui/connection/ConnectionSetupController.kt").readText()
        val connectionScreen = File("src/main/java/io/codecks/ui/connection/ConnectionScreen.kt").readText()

        assertTrue(store.contains("getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)"))
        assertTrue(viewModel.contains("recordSetupPass("))
        assertTrue(controller.contains("viewModel.resumeSetupStep()"))
        assertTrue(connectionScreen.contains("state.setupSnapshot"))
        forbiddenTokens.forEach { token ->
            assertFalse(store.contains("val $token", ignoreCase = true))
        }
    }

    @Test
    fun writesSetupResumeEvidence() {
        val rows = JSONArray()
        SetupStep.entries.forEachIndexed { index, firstMissing ->
            val receipts = SetupStep.entries
                .take(index)
                .associateWith { step -> receipt(step, SetupReceiptResult.Passed, now - 100L + step.ordinal) }
            val snapshot = SetupSnapshot(receipts = receipts)
            rows.put(
                JSONObject()
                    .put("case", "resume_${firstMissing.persistedCode}")
                    .put("firstMandatoryNotPassed", snapshot.firstMandatoryNotPassed?.persistedCode)
                    .put("complete", snapshot.isComplete),
            )
        }
        val complete = SetupSnapshot(
            receipts = SetupStep.entries.associateWith { step ->
                receipt(step, SetupReceiptResult.Passed, now - 200L + step.ordinal)
            },
        )
        rows.put(
            JSONObject()
                .put("case", "complete")
                .put("firstMandatoryNotPassed", JSONObject.NULL)
                .put("complete", complete.isComplete),
        )
        val output = evidenceDirectory().resolve("setup_resume_matrix.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(rows.toString(2))

        assertEquals(SetupStep.entries.size + 1, rows.length())
        assertTrue(output.isFile)
    }

    private fun receipt(
        step: SetupStep,
        result: SetupReceiptResult,
        completedAtMillis: Long,
    ): SetupReceipt = SetupReceipt(
        step = step,
        result = result,
        completedAtMillis = completedAtMillis,
    )

    private fun evidenceDirectory(): File {
        val moduleDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return requireNotNull(moduleDirectory.parentFile).resolve("build/ga-evidence/SET-01")
    }

    private companion object {
        val forbiddenTokens = listOf(
            "credential",
            "password",
            "host",
            "identity",
            "clipboard",
            "prompt",
            "path",
            "output",
        )
    }
}
