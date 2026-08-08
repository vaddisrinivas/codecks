package io.codecks.commercial.auth

import io.codecks.commercial.sync.ProductionPlaySnapshotAdapter
import io.codecks.domain.commercial.AccountDeletionResult
import io.codecks.domain.commercial.AccountSessionResult
import io.codecks.domain.commercial.AccountSignInResult
import io.codecks.domain.commercial.AccountSignOutResult
import io.codecks.domain.commercial.BackendAccountId
import io.codecks.domain.commercial.CloudSnapshotHandle
import io.codecks.domain.commercial.CommercialDenyReason
import io.codecks.domain.commercial.CommercialOperationId
import io.codecks.domain.commercial.LocalSnapshotHandle
import io.codecks.domain.commercial.SnapshotChecksum
import io.codecks.domain.commercial.SnapshotDownloadRequest
import io.codecks.domain.commercial.SnapshotRestorePreviewRequest
import io.codecks.domain.commercial.SnapshotRestorePreviewResult
import io.codecks.domain.commercial.SnapshotTransferResult
import io.codecks.domain.commercial.SnapshotUploadRequest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionPlayCommercialAdaptersTest {
    @Test
    fun allProductionActionsDenyBeforeConstructingAnyClientOrStore() = runTest {
        var constructions = 0
        val account = ProductionPlayAccountAdapter(
            deletionUrl = AccountDeletionUrl.https("https://codecks.app/account/delete"),
            credentialManagerFactory = { constructions++; error("unreachable") },
            transportFactory = { constructions++; error("unreachable") },
            sessionStoreFactory = { constructions++; error("unreachable") },
        )
        val snapshots = ProductionPlaySnapshotAdapter(
            cloudFactory = { constructions++; error("unreachable") },
            snapshotStoreFactory = { constructions++; error("unreachable") },
        )
        val operation = CommercialOperationId.trusted("operation-production-dark")
        val accountId = BackendAccountId.verified("account-production-dark")

        assertTrue(account.currentSession() is AccountSessionResult.Unavailable)
        assertDenied(account.signIn(operation))
        assertDenied(account.signOut(operation))
        assertDenied(account.deleteAccount(operation))
        assertDenied(
            snapshots.upload(
                SnapshotUploadRequest(
                    operation,
                    accountId,
                    LocalSnapshotHandle.trusted("local-production-dark"),
                    SnapshotChecksum("a".repeat(64)),
                    1,
                    1,
                ),
            ),
        )
        assertDenied(
            snapshots.download(
                SnapshotDownloadRequest(operation, accountId, CloudSnapshotHandle.trusted("cloud-production-dark")),
            ),
        )
        assertDenied(
            snapshots.previewRestore(
                SnapshotRestorePreviewRequest(operation, accountId, LocalSnapshotHandle.trusted("local-preview-dark")),
            ),
        )
        assertEquals(0, constructions)
    }

    @Test
    fun deletionUrlRejectsNonPublicOrCredentialBearingValues() {
        assertThrows(IllegalArgumentException::class.java) { AccountDeletionUrl.https("http://codecks.app/delete") }
        assertThrows(IllegalArgumentException::class.java) { AccountDeletionUrl.https("https://user@codecks.app/delete") }
        assertThrows(IllegalArgumentException::class.java) { AccountDeletionUrl.https("https://codecks.app/delete?account=1") }
        assertThrows(IllegalArgumentException::class.java) { AccountDeletionUrl.https("https:///delete") }
        assertThrows(IllegalArgumentException::class.java) { AccountDeletionUrl.https("https://localhost/delete") }
        assertThrows(IllegalArgumentException::class.java) { AccountDeletionUrl.https("https://127.0.0.1/delete") }
        assertThrows(IllegalArgumentException::class.java) { AccountDeletionUrl.https("https://codecks.app:8443/delete") }
    }

    @Test
    fun productionAdaptersHaveNoSdkStartupProviderWorkerOrCurrentRouteWiring() {
        val projectRoot = Path.of(System.getProperty("user.dir"))
        val playSource = projectRoot.resolve("src/play/java/io/codecks/commercial")
        val production = Files.walk(playSource).use { paths ->
            paths.filter { it.extension == "kt" }.map { it.readText() }.toList().joinToString("\n")
        }
        val main = Files.walk(projectRoot.resolve("src/main/java")).use { paths ->
            paths.filter { it.extension == "kt" || it.extension == "java" }
                .map { it.readText() }
                .toList()
                .joinToString("\n")
        }
        listOf(
            "import android.",
            "import androidx.credentials",
            "com.google.firebase",
            "com.google.android.gms",
            "WorkManager",
            "ContentProvider",
            "OkHttpClient",
            "Retrofit",
        ).forEach { marker -> assertTrue("Unexpected production dependency: $marker", marker !in production) }
        assertTrue("ProductionPlayAccountAdapter" !in main)
        assertTrue("ProductionPlaySnapshotAdapter" !in main)
    }

    private fun assertDenied(result: Any) {
        val reason = when (result) {
            is AccountSignInResult.Denied -> result.reason
            is AccountSignOutResult.Denied -> result.reason
            is AccountDeletionResult.Denied -> result.reason
            is SnapshotTransferResult.Denied -> result.reason
            is SnapshotRestorePreviewResult.Denied -> result.reason
            else -> error("Expected denial, got $result")
        }
        assertEquals(CommercialDenyReason.BUILD_PRODUCTION_DARK, reason)
    }
}
