package io.codecks.domain.reactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpotlightSftpModelsTest {
    private val provenance = ReactiveRequestProvenance(
        macId = MacId("123e4567-e89b-12d3-a456-426614174000"),
        snapshotRevision = 1L,
        source = StateSource.Helper,
        observedAtMillis = 1_000L,
    )

    @Test
    fun spotlightQueryRejectsPathAndShellInjectionShapes() {
        listOf(
            "../secrets",
            "/Users/me",
            "deck; rm -rf x",
            "deck\nother",
            "deck | pbcopy",
            "~/.ssh",
        ).forEach { query ->
            assertThrows(IllegalArgumentException::class.java) {
                SpotlightSearchRequest(query = query, provenance = provenance)
            }
        }
    }

    @Test
    fun spotlightQueryCapsLengthAndResults() {
        assertThrows(IllegalArgumentException::class.java) {
            SpotlightSearchRequest(query = "x".repeat(97), provenance = provenance)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpotlightSearchRequest(query = "deck", maxResults = 21, provenance = provenance)
        }

        val valid = SpotlightSearchRequest(query = "Quarterly Deck", maxResults = 20, provenance = provenance)

        assertEquals(20, valid.maxResults)
    }

    @Test
    fun sftpPathsMustStayUnderAllowlistedRoots() {
        val roots = SftpAllowedRoots(
            localRootId = "mac_downloads",
            localRoot = "/Users/me/Downloads",
            remoteRootId = "phone_inbox",
            remoteRoot = "/phone/inbox",
        )

        assertThrows(IllegalArgumentException::class.java) {
            SafeSftpTransferRequest(
                direction = TransferDirection.MacToPhone,
                localPath = "/Users/me/.ssh/id_rsa",
                remotePath = "/phone/inbox/id_rsa",
                roots = roots,
                provenance = provenance,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SafeSftpTransferRequest(
                direction = TransferDirection.MacToPhone,
                localPath = "/Users/me/Downloads/../secrets.txt",
                remotePath = "/phone/inbox/secrets.txt",
                roots = roots,
                provenance = provenance,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SafeSftpTransferRequest(
                direction = TransferDirection.MacToPhone,
                localPath = "/Users/me/Downloads/report\u0001.pdf",
                remotePath = "/phone/inbox/report.pdf",
                roots = roots,
                provenance = provenance,
            )
        }

        val valid = SafeSftpTransferRequest(
            direction = TransferDirection.MacToPhone,
            localPath = "/Users/me/Downloads/report.pdf",
            remotePath = "/phone/inbox/report.pdf",
            roots = roots,
            provenance = provenance,
        )

        assertEquals(TransferDirection.MacToPhone, valid.direction)
    }
}
