package io.codecks.internalquality.m16

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/** DUMP-permission read-only bridge; host can attest exact worker-owned bytes. */
class M16EvidenceProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r") { "read_only" }
        val segments = uri.pathSegments
        require(segments.size == 3 && segments[0] == "profile") { "invalid_path" }
        val profileId = segments[1]
        require(profileId.matches(Regex("avd0[1-4]-p0[1-5]"))) { "invalid_profile" }
        require(segments[2] in setOf("ledger.jsonl", "checkpoint.json", "repo-probe.json")) { "invalid_artifact" }
        val base = requireNotNull(context).filesDir.resolve("m16/profiles").canonicalFile
        val target = File(base, "$profileId/${segments[2]}").canonicalFile
        require(target.path.startsWith(base.path + File.separator) && target.isFile) { "artifact_missing" }
        return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/octet-stream"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("read_only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("read_only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("read_only")
}
