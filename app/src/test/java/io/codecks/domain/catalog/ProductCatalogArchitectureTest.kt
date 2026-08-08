package io.codecks.domain.catalog

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCatalogArchitectureTest {
    @Test
    fun catalogDomainHasNoAndroidUiNetworkAccountOrCommercialAuthorizationDependency() {
        val domainSources = listOf(
            File("src/main/java/io/codecks/domain/catalog"),
            File("src/main/java/io/codecks/domain/sshpack"),
        ).flatMap { it.walkTopDown().filter { file -> file.isFile && file.extension == "kt" }.toList() }
            .joinToString("\n") { it.readText() }

        listOf(
            "import android.",
            "import androidx.",
            "io.codecks.ui",
            "io.codecks.data",
            "java.net",
            "okhttp3",
            "retrofit2",
            "AccountSession",
            "EntitlementProjection",
            "CommercialDecision",
        ).forEach { forbidden -> assertFalse(forbidden, domainSources.contains(forbidden)) }
        assertTrue(domainSources.contains("PREMIUM_DISPLAY_ONLY"))
        assertTrue(domainSources.contains("val permitsLocalUse: Boolean get() = true"))
    }

    @Test
    fun localCatalogContractsExposeNoAccountOrNetworkRequirement() {
        val repository = File("src/main/java/io/codecks/domain/catalog/CatalogContracts.kt").readText()
            .substringAfter("interface ProductCatalogRepository")
        assertFalse(repository.contains("AccountId"))
        assertFalse(repository.contains("AccountSession"))
        assertFalse(repository.contains("NetworkClient"))
        assertFalse(repository.contains("suspend"))
    }
}
