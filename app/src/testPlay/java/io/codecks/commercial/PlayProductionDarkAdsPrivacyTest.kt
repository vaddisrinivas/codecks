package io.codecks.commercial

import io.codecks.commercial.ads.productionAdsService
import io.codecks.commercial.privacy.productionPrivacyService
import io.codecks.domain.commercial.AdEligibilityRequest
import io.codecks.domain.commercial.AdEligibilityResult
import io.codecks.domain.commercial.AdIneligibilityCode
import io.codecks.domain.commercial.ApprovedAdPlacement
import io.codecks.domain.commercial.CommercialDecision
import io.codecks.domain.commercial.CommercialOperationId
import io.codecks.domain.commercial.CommercialSurface
import io.codecks.domain.commercial.PrivacyActionResult
import io.codecks.domain.commercial.PrivacyConsentStatus
import io.codecks.domain.commercial.PrivacyStatusResult
import io.codecks.domain.commercial.CommercialUnavailableCode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayProductionDarkAdsPrivacyTest {
    @Test
    fun `public Play privacy remains immutable production dark`() = runTest {
        val service = productionPrivacyService()
        val operationId = CommercialOperationId.trusted("privacy-dark-001")

        assertEquals(
            PrivacyStatusResult.Unavailable(CommercialUnavailableCode.PRODUCTION_DARK),
            service.currentStatus(),
        )
        assertTrue(service.requestConsent(operationId) is PrivacyActionResult.Denied)
        assertTrue(service.openPrivacyOptions(operationId) is PrivacyActionResult.Denied)
    }

    @Test
    fun `public Play ads deny even an otherwise eligible request`() {
        val result = productionAdsService().evaluate(
            AdEligibilityRequest(
                placement = ApprovedAdPlacement.ROUTINE_BANK_CARD,
                commercialDecision = CommercialDecision.Allowed(CommercialSurface.ADS),
                consentStatus = PrivacyConsentStatus.GRANTED,
                isForeground = true,
                organicItemsBefore = 100,
                userRequestedReward = true,
                hasAdFreeEntitlement = false,
            ),
        )

        assertEquals(
            AdEligibilityResult.Denied(AdIneligibilityCode.PRODUCTION_DARK),
            result,
        )
    }

    @Test
    fun `public Play source has no SDK provider worker network or config construction`() {
        val projectRoot = Path.of(System.getProperty("user.dir"))
        val sourceRoot = projectRoot.resolve("src/play/java")
        val combined = Files.walk(sourceRoot).use { paths ->
            paths.filter { it.extension == "kt" }.map { it.readText() }.toList().joinToString("\n")
        }
        val forbidden = listOf(
            "MobileAds",
            "AdRequest",
            "UserMessagingPlatform",
            "ConsentRequestParameters",
            "WorkManager",
            "Worker",
            "HttpClient",
            "OkHttpClient",
            "Retrofit",
            "FirebaseRemoteConfig",
            "Configuration(",
        )

        forbidden.forEach { marker -> assertTrue("Unexpected $marker", marker !in combined) }
    }
}
