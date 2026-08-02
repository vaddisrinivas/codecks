package io.codecks.data.privacy

import java.io.File
import java.io.FileOutputStream

class SupportBundleTempFilePolicy(
    cacheDirectory: File,
) {
    private val directory = File(cacheDirectory, DIRECTORY_NAME)

    fun write(bytes: ByteArray, nowEpochMs: Long): Result<File> = runCatching {
        require(bytes.isNotEmpty()) { "Support bundle is empty" }
        check(directory.exists() || directory.mkdirs()) { "Cannot create support cache" }
        val finalFile = File(directory, "$FILE_PREFIX$nowEpochMs.zip")
        val staging = File(directory, ".$FILE_PREFIX$nowEpochMs.tmp")
        FileOutputStream(staging).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        check(staging.renameTo(finalFile)) {
            staging.delete()
            "Cannot finalize support bundle"
        }
        finalFile
    }

    fun cancel(file: File?) {
        file?.takeIf(::isOwnedFile)?.delete()
    }

    fun cleanupExpired(
        nowEpochMs: Long,
        maxAgeMillis: Long = MAX_AGE_MILLIS,
    ): Int {
        if (!directory.isDirectory) return 0
        var deleted = 0
        directory.listFiles().orEmpty()
            .filter(::isOwnedFile)
            .filter { nowEpochMs - it.lastModified() > maxAgeMillis }
            .forEach { if (it.delete()) deleted += 1 }
        return deleted
    }

    internal fun isOwnedFile(file: File): Boolean =
        file.parentFile == directory &&
            (file.name.startsWith(FILE_PREFIX) || file.name.startsWith(".$FILE_PREFIX")) &&
            (file.name.endsWith(".zip") || file.name.endsWith(".tmp"))

    companion object {
        const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L
        private const val DIRECTORY_NAME = "support-bundles"
        private const val FILE_PREFIX = "codecks-support-"
    }
}
