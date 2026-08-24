package io.codecks

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.codecks.data.ConnectionRepository
import io.codecks.domain.contextdeck.FileDropItem
import io.codecks.domain.contextdeck.FileDropShelf
import io.codecks.domain.device.DeviceId
import io.codecks.domain.reactive.MacId
import io.codecks.domain.reactive.ReactiveRequestProvenance
import io.codecks.domain.reactive.SafeSftpTransferRequest
import io.codecks.domain.reactive.SftpAllowedRoots
import io.codecks.domain.reactive.StateSource
import io.codecks.domain.reactive.TransferDirection
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun rememberFileDropLauncher(
    appContext: Context,
    connectionRepository: ConnectionRepository,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
): (DeviceId) -> Unit {
    var pendingTarget by remember { mutableStateOf<DeviceId?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val target = pendingTarget
        pendingTarget = null
        if (target != null && uris.isNotEmpty()) scope.launch {
            val result = withContext(Dispatchers.IO) {
                sendFileDrop(appContext, connectionRepository, target, uris)
            }
            snackbarHostState.showSnackbar(
                result.fold(
                    onSuccess = { count -> "$count file${if (count == 1) "" else "s"} sent to Mac" },
                    onFailure = { it.message ?: "File Drop failed" },
                ),
            )
        }
    }
    return { target ->
        pendingTarget = target
        launcher.launch(arrayOf("*/*"))
    }
}

internal suspend fun sendFileDrop(
    context: Context,
    connectionRepository: ConnectionRepository,
    targetId: DeviceId,
    uris: List<Uri>,
): Result<Int> {
    val boundedUris = uris.take(FileDropShelf.MAX_ITEMS)
    if (boundedUris.size != uris.size) return Result.failure(IllegalArgumentException("Choose at most ${FileDropShelf.MAX_ITEMS} files"))
    val root = File(context.cacheDir, "file-drop")
    val invocation = File(root, UUID.randomUUID().toString())
    return try {
        root.mkdirs()
        require(root.isDirectory && !Files.isSymbolicLink(root.toPath())) { "File Drop cache unavailable" }
        invocation.mkdirs()
        require(invocation.isDirectory && !Files.isSymbolicLink(invocation.toPath()) && invocation.canonicalFile.parentFile == root.canonicalFile) {
            "File Drop cache unavailable"
        }
        val staged = buildList {
            var totalBytes = 0L
            boundedUris.forEachIndexed { index, uri ->
                val file = stageDropFile(context, uri, invocation, index)
                totalBytes += file.item.byteCount
                require(totalBytes <= FileDropShelf.MAX_TOTAL_BYTES) { "File Drop exceeds 250 MB" }
                add(file)
            }
        }
        FileDropShelf(staged.map(StagedDropFile::item), targetId, REMOTE_ROOT_ID)
        val batchId = System.currentTimeMillis()
        staged.forEach { file ->
            val request = SafeSftpTransferRequest(
                direction = TransferDirection.PhoneToMac,
                localPath = file.file.canonicalPath,
                remotePath = "$REMOTE_ROOT/codecks-$batchId-${file.item.displayName}",
                roots = SftpAllowedRoots(
                    localRootId = LOCAL_ROOT_ID,
                    localRoot = root.canonicalPath,
                    remoteRootId = REMOTE_ROOT_ID,
                    remoteRoot = REMOTE_ROOT,
                ),
                provenance = ReactiveRequestProvenance(
                    macId = MacId(targetId.value),
                    snapshotRevision = 0,
                    source = StateSource.LocalCache,
                    observedAtMillis = batchId,
                ),
                maxBytes = file.item.byteCount,
            )
            connectionRepository.runSftpTransferOnTarget(targetId.value, request).getOrThrow()
        }
        Result.success(staged.size)
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        Result.failure(error)
    } finally {
        invocation.deleteRecursively()
    }
}

private fun stageDropFile(context: Context, uri: Uri, invocation: File, index: Int): StagedDropFile {
    val metadata = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) null else {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            (nameIndex.takeIf { it >= 0 }?.let(cursor::getString)) to
                (sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong))
        }
    }
    val displayName = safeDropFileName(metadata?.first, index)
    val declaredSize = metadata?.second
    require(declaredSize == null || declaredSize in 1..FileDropItem.MAX_FILE_BYTES) { "$displayName is too large or empty" }
    val destination = File(invocation, displayName)
    require(destination.canonicalFile.parentFile == invocation.canonicalFile) { "Unsafe File Drop name" }
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        destination.outputStream().use { output -> input.copyBoundedTo(output, FileDropItem.MAX_FILE_BYTES) }
    } ?: error("Could not read $displayName")
    require(bytes > 0) { "$displayName is empty" }
    require(declaredSize == null || declaredSize == bytes) { "$displayName changed while being read" }
    val item = FileDropItem(
        uriToken = "drop:$index",
        displayName = displayName,
        byteCount = bytes,
        mimeType = context.contentResolver.getType(uri),
    )
    return StagedDropFile(item, destination)
}

internal fun safeDropFileName(candidate: String?, index: Int): String {
    val normalized = candidate.orEmpty().trim().take(88)
    return if (
        normalized.isNotBlank() && normalized !in setOf(".", "..") &&
        normalized.none { it.isISOControl() || it == '/' || it == '\\' }
    ) "${index + 1}-$normalized" else "file-${index + 1}"
}

private fun InputStream.copyBoundedTo(output: java.io.OutputStream, maxBytes: Long): Long {
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) return total
        total += read
        require(total <= maxBytes) { "File exceeds ${maxBytes / (1024 * 1024)} MB" }
        output.write(buffer, 0, read)
    }
}

private data class StagedDropFile(val item: FileDropItem, val file: File)

private const val LOCAL_ROOT_ID = "codecks_file_drop"
private const val REMOTE_ROOT_ID = "mac_shared"
private const val REMOTE_ROOT = "/Users/Shared"
