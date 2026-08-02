package io.codecks.data.privacy

import java.io.File
import java.io.FileOutputStream

class SupportBundleTempFilePolicy(
    cacheDirectory: File,
) {
    private val directory = File(cacheDirectory, DIRECTORY_NAME)

    fun write(bytes: ByteArray, nowEpochMs: Long): Result<File> = try {
        Result.success(
            run {
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
            },
        )
    } catch (error: Throwable) {
        when (error) {
            is VirtualMachineError,
            is ThreadDeath,
            is LinkageError,
            -> throw error
        }
        Result.failure(error)
    }

    fun cancel(file: File?): Boolean =
        file == null || (isOwnedFile(file) && (!file.exists() || file.delete()) && !file.exists())

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

    fun pendingFiles(): List<File> = directory.listFiles()
        .orEmpty()
        .asSequence()
        .filter(::isOwnedFile)
        .filter { it.isFile && it.name.endsWith(".zip") }
        .sortedByDescending(File::lastModified)
        .toList()

    fun latestPending(): File? = pendingFiles().firstOrNull()

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
