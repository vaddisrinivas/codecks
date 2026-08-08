package io.codecks.domain.commercial

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayReleaseCommercialAdapterArchitectureTest {
    @Test
    fun publicPlayAdaptersCannotConstructOrInitializeCommercialClients() {
        val source = File("src/play/java/io/codecks/commercial")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        listOf(
            "BillingClient",
            "IntegrityManager",
            "android.content.Context",
            "com.android.billingclient",
            "com.google.android.play.integrity",
        ).forEach { forbidden -> assertFalse(forbidden, source.contains(forbidden, ignoreCase = true)) }
        assertFalse(
            Regex("""\b(?:fun\s+)?(?:connect|initialize|start)\s*\(""", RegexOption.IGNORE_CASE)
                .containsMatchIn(source),
        )
        assertTrue(source.contains("ProductionDarkPurchaseService"))
        assertTrue(source.contains("ProductionDarkEntitlementService"))
        assertTrue(source.contains("ProductionDarkIntegrityService"))
    }

    @Test
    fun internalEntitlementAuthorityIsConfinedToBackendAdapter() {
        val root = File("src/playInternal/java")
        if (!root.exists()) return
        val authorityCallers = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("ServerEntitlementState.verified(") }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toList()

        assertTrue(
            authorityCallers.toString(),
            authorityCallers == listOf(
                "io/codecks/internalcommercial/entitlement/InternalEntitlementSandbox.kt",
            ),
        )
    }
}
