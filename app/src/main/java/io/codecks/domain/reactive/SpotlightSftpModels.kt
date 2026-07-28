package io.codecks.domain.reactive

enum class TransferDirection {
    MacToPhone,
    PhoneToMac,
}

data class ReactiveRequestProvenance(
    val macId: MacId,
    val snapshotRevision: Long,
    val source: StateSource,
    val observedAtMillis: Long,
) {
    init {
        require(snapshotRevision >= 0) { "ReactiveRequestProvenance snapshotRevision must be non-negative." }
        require(observedAtMillis >= 0) { "ReactiveRequestProvenance observedAtMillis must be non-negative." }
    }
}

data class SpotlightSearchRequest(
    val query: String,
    val maxResults: Int = DefaultMaxResults,
    val provenance: ReactiveRequestProvenance,
) {
    init {
        SpotlightSftpPolicy.validateSpotlightQuery(query)
        require(maxResults in 1..MaxResultsCap) {
            "SpotlightSearchRequest maxResults must be between 1 and $MaxResultsCap."
        }
    }

    companion object {
        const val DefaultMaxResults: Int = 8
        const val MaxResultsCap: Int = 20
    }
}

data class SftpAllowedRoots(
    val localRootId: String,
    val localRoot: String,
    val remoteRootId: String,
    val remoteRoot: String,
) {
    init {
        require(localRootId.matches(SpotlightSftpPolicy.SafeRootId)) { "SFTP localRootId is invalid." }
        require(remoteRootId.matches(SpotlightSftpPolicy.SafeRootId)) { "SFTP remoteRootId is invalid." }
        require(SpotlightSftpPolicy.isSafeAbsolutePath(localRoot)) { "SFTP localRoot is invalid." }
        require(SpotlightSftpPolicy.isSafeAbsolutePath(remoteRoot)) { "SFTP remoteRoot is invalid." }
    }
}

data class SafeSftpTransferRequest(
    val direction: TransferDirection,
    val localPath: String,
    val remotePath: String,
    val roots: SftpAllowedRoots,
    val provenance: ReactiveRequestProvenance,
    val maxBytes: Long = DefaultMaxBytes,
) {
    init {
        require(maxBytes in 1..MaxBytesCap) {
            "SafeSftpTransferRequest maxBytes must be between 1 and $MaxBytesCap."
        }
        require(SpotlightSftpPolicy.isUnderRoot(localPath, roots.localRoot)) {
            "SFTP localPath must be under allowlisted localRoot."
        }
        require(SpotlightSftpPolicy.isUnderRoot(remotePath, roots.remoteRoot)) {
            "SFTP remotePath must be under allowlisted remoteRoot."
        }
    }

    companion object {
        const val DefaultMaxBytes: Long = 25L * 1024L * 1024L
        const val MaxBytesCap: Long = 100L * 1024L * 1024L
    }
}

object SpotlightSftpPolicy {
    const val MaxSpotlightQueryChars: Int = 96
    val SafeRootId = Regex("[A-Za-z0-9_.-]{1,48}")

    private val blockedQueryChars = setOf('/', '\\', '~', '$', '`', ';', '|', '&', '<', '>', '\'', '"')
    private val blockedPathChars = setOf('\u0000', '\n', '\r', '`', ';', '|', '&', '<', '>')

    fun validateSpotlightQuery(query: String) {
        require(query.isNotBlank()) { "Spotlight query must not be blank." }
        require(query.length <= MaxSpotlightQueryChars) {
            "Spotlight query must be at most $MaxSpotlightQueryChars chars."
        }
        require(query.none { it.isISOControl() || it in blockedQueryChars }) {
            "Spotlight query contains unsafe characters."
        }
        require(".." !in query) { "Spotlight query must not contain path traversal." }
    }

    fun isSafeAbsolutePath(path: String): Boolean =
        path.startsWith("/") &&
            path.length in 2..512 &&
            path.none { it.isISOControl() || it in blockedPathChars } &&
            path.split('/').none { it == ".." }

    fun isUnderRoot(path: String, root: String): Boolean {
        if (!isSafeAbsolutePath(path) || !isSafeAbsolutePath(root)) return false
        val normalizedRoot = root.trimEnd('/')
        val normalizedPath = path.trimEnd('/')
        return normalizedPath == normalizedRoot || normalizedPath.startsWith("$normalizedRoot/")
    }
}
