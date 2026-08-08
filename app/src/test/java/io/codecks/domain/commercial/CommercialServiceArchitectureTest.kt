package io.codecks.domain.commercial

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialServiceArchitectureTest {
    @Test
    fun contractsHaveNoPlatformSdkOrNetworkDependency() {
        val sources = File("src/main/java/io/codecks/domain/commercial")
            .walkTopDown()
            .filter { it.isFile && it.name != "CommercialExecutionPolicy.kt" && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        listOf(
            "import android.",
            "import androidx.",
            "com.android.billingclient",
            "com.google.android",
            "com.google.firebase",
            "okhttp3",
            "retrofit2",
            "java.net",
        ).forEach { forbidden -> assertFalse(forbidden, sources.contains(forbidden)) }
        assertFalse(sources.contains("LocalAction"))
        assertFalse(sources.contains("Trackpad"))
        assertFalse(sources.contains("Clipboard"))
        assertFalse(sources.contains("Ssh"))
    }

    @Test
    fun darkNoOpsHaveNoInjectedHooksOrStartupMethod() {
        val source = File(
            "src/main/java/io/codecks/domain/commercial/ProductionDarkCommercialServices.kt",
        ).readText()

        assertFalse(source.contains("constructor("))
        assertFalse(source.contains("fun start"))
        assertFalse(source.contains("fun initialize"))
        assertFalse(source.contains("suspend fun fetch"))
        assertTrue(source.contains("ProductionDarkOperationalConfigService"))
        assertTrue(source.contains("ProductionDarkAdEligibilityService"))
    }

    @Test
    fun accountContractContainsNoEmailOrCredentialMaterial() {
        val source = File(
            "src/main/java/io/codecks/domain/commercial/AccountServiceContracts.kt",
        ).readText().lowercase()

        assertFalse(source.contains("email"))
        assertFalse(source.contains("password"))
        assertFalse(source.contains("access" + "token"))
        assertFalse(source.contains("refresh" + "token"))
    }

    @Test
    fun purchaseLaunchContractCannotReturnServerEntitlementState() {
        val source = File(
            "src/main/java/io/codecks/domain/commercial/EntitlementPurchaseIntegrityContracts.kt",
        ).readText()
        val launchResult = source.substringAfter("sealed interface PurchaseLaunchResult")
            .substringBefore("data class CanonicalRequestDigest")

        assertFalse(launchResult.contains("ServerEntitlementState"))
        assertFalse(launchResult.contains("PaidCapability"))
        assertFalse(launchResult.contains("ServerStateRecorded"))
        assertTrue(launchResult.contains("AwaitingServerVerification"))
    }

    @Test
    fun snapshotContractUsesHandlesAndNeverCarriesRawSnapshotPayloads() {
        val source = File(
            "src/main/java/io/codecks/domain/commercial/SnapshotServiceContracts.kt",
        ).readText()

        listOf(
            "ByteArray",
            "Map<",
            "JsonObject",
            "JsonElement",
            "payload",
            "serialized",
        ).forEach { forbidden -> assertFalse(forbidden, source.contains(forbidden, ignoreCase = true)) }
        assertTrue(source.contains("LocalSnapshotHandle"))
        assertTrue(source.contains("CloudSnapshotHandle"))
        assertTrue(source.contains("SnapshotChecksum"))
    }

    @Test
    fun contractFailuresAndDiagnosticsDoNotCarrySecretsOrRawDetails() {
        val sources = File("src/main/java/io/codecks/domain/commercial")
            .walkTopDown()
            .filter { it.isFile && it.name != "CommercialExecutionPolicy.kt" && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        listOf(
            "Throwable",
            "Exception",
            "stackTrace",
            "errorBody",
            "access" + "token",
            "refresh" + "token",
            "credential",
        ).forEach { forbidden -> assertFalse(forbidden, sources.contains(forbidden, ignoreCase = true)) }
    }
}
