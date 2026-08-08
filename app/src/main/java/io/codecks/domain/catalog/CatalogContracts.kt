package io.codecks.domain.catalog

import io.codecks.domain.sshpack.SshActionPack
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections

private val STABLE_ID = Regex("[a-z][a-z0-9_.-]{2,63}")
private val SHA_256 = Regex("[0-9a-f]{64}")
private val HEX_COLOR = Regex("#[0-9A-Fa-f]{6}")

@JvmInline
value class CatalogId(val value: String) : Comparable<CatalogId> {
    init {
        require(value.matches(STABLE_ID))
    }

    override fun compareTo(other: CatalogId): Int = value.compareTo(other.value)
}

data class CatalogVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<CatalogVersion> {
    init {
        require(major >= 0 && minor >= 0 && patch >= 0)
    }

    override fun compareTo(other: CatalogVersion): Int =
        compareValuesBy(this, other, CatalogVersion::major, CatalogVersion::minor, CatalogVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"
}

@JvmInline
value class CatalogPayloadDigest(val sha256: String) {
    init {
        require(sha256.matches(SHA_256))
    }
}

class CatalogSignature private constructor(private val canonical: String) {
    private val encoded: ByteArray

    init {
        require(canonical.matches(Regex("[A-Za-z0-9_-]{32,512}")))
        encoded = runCatching { Base64.getUrlDecoder().decode(canonical) }
            .getOrElse { throw IllegalArgumentException("Catalog signature must be base64url", it) }
        require(encoded.isNotEmpty())
    }

    override fun toString(): String = "CatalogSignature(redacted)"

    fun encoded(): ByteArray = encoded.copyOf()

    companion object {
        fun fromTransport(value: String): CatalogSignature = CatalogSignature(value)
    }
}

/** Immutable canonical bytes presented to a signature adapter. */
class CatalogSignedPayload internal constructor(
    val digest: CatalogPayloadDigest,
    canonicalBytes: ByteArray,
) {
    private val canonicalBytes = canonicalBytes.copyOf()

    fun encoded(): ByteArray = canonicalBytes.copyOf()
}

sealed interface CatalogSource {
    data object Bundled : CatalogSource
    data class SignedPublisher(val publisherId: CatalogId) : CatalogSource
}

enum class CatalogAccessClass {
    FREE,
    PREMIUM_DISPLAY_ONLY,
    ;

    /** Commercial metadata is never an authorization gate while premium enforcement is dark. */
    val permitsLocalUse: Boolean get() = true
}

data class CatalogAccessibility(
    val talkBackLabel: String,
    val minimumTouchTargetDp: Int,
    val minimumContrastRatio: Double,
    val supportsReducedMotion: Boolean,
    val colorIndependentMeaning: Boolean,
) {
    init {
        require(talkBackLabel.isNotBlank())
        require(minimumTouchTargetDp >= 48)
        require(minimumContrastRatio in 4.5..21.0)
    }
}

enum class CatalogPlatform {
    ANDROID,
}

data class CatalogEnvironment(
    val platform: CatalogPlatform,
    val appVersionCode: Int,
    val osApiLevel: Int,
) {
    init {
        require(appVersionCode > 0)
        require(osApiLevel > 0)
    }
}

data class CatalogCompatibility(
    val platform: CatalogPlatform = CatalogPlatform.ANDROID,
    val minimumAppVersionCode: Int,
    val maximumAppVersionCode: Int? = null,
    val minimumOsApiLevel: Int,
) {
    init {
        require(minimumAppVersionCode > 0)
        require(maximumAppVersionCode == null || maximumAppVersionCode >= minimumAppVersionCode)
        require(minimumOsApiLevel > 0)
    }

    fun supports(environment: CatalogEnvironment): Boolean =
        environment.platform == platform &&
            environment.appVersionCode >= minimumAppVersionCode &&
            (maximumAppVersionCode == null || environment.appVersionCode <= maximumAppVersionCode) &&
            environment.osApiLevel >= minimumOsApiLevel
}

sealed interface ProductCatalogEntry {
    val id: CatalogId
    val title: String
    val summary: String
    val contentRevision: Int
    val accessClass: CatalogAccessClass
    val compatibility: CatalogCompatibility
    val accessibility: CatalogAccessibility

    class Routine(
        override val id: CatalogId,
        override val title: String,
        override val summary: String,
        override val contentRevision: Int,
        override val accessClass: CatalogAccessClass,
        override val compatibility: CatalogCompatibility,
        override val accessibility: CatalogAccessibility,
        actionIds: List<CatalogId>,
    ) : ProductCatalogEntry {
        val actionIds: List<CatalogId> = Collections.unmodifiableList(actionIds.toList())

        init {
            validateCommon()
            require(this.actionIds.isNotEmpty())
            require(this.actionIds.distinct().size == this.actionIds.size)
        }
    }

    class Theme(
        override val id: CatalogId,
        override val title: String,
        override val summary: String,
        override val contentRevision: Int,
        override val accessClass: CatalogAccessClass,
        override val compatibility: CatalogCompatibility,
        override val accessibility: CatalogAccessibility,
        val presetId: CatalogId,
        previewColors: List<String>,
        val motionIntensity: ThemeMotionIntensity,
        val contrastClass: ThemeContrastClass,
    ) : ProductCatalogEntry {
        val previewColors: List<String> = Collections.unmodifiableList(previewColors.toList())

        init {
            validateCommon()
            require(this.previewColors.size in 2..8)
            require(this.previewColors.all { it.matches(HEX_COLOR) })
            require(motionIntensity != ThemeMotionIntensity.FULL || accessibility.supportsReducedMotion)
        }
    }

    data class SshPack(
        override val id: CatalogId,
        override val title: String,
        override val summary: String,
        override val contentRevision: Int,
        override val accessClass: CatalogAccessClass,
        override val compatibility: CatalogCompatibility,
        override val accessibility: CatalogAccessibility,
        val pack: SshActionPack,
    ) : ProductCatalogEntry {
        init {
            validateCommon()
            require(pack.id == id)
        }
    }
}

private fun ProductCatalogEntry.validateCommon() {
    require(title.isNotBlank())
    require(summary.isNotBlank())
    require(contentRevision > 0)
}

enum class ThemeMotionIntensity {
    NONE,
    REDUCED,
    FULL,
}

enum class ThemeContrastClass {
    STANDARD_AA,
    HIGH_CONTRAST,
}

class CatalogBundle(
    val id: CatalogId,
    val version: CatalogVersion,
    val source: CatalogSource,
    val payloadDigest: CatalogPayloadDigest,
    val signature: CatalogSignature?,
    entries: List<ProductCatalogEntry>,
) {
    val entries: List<ProductCatalogEntry> = Collections.unmodifiableList(entries.sortedBy { it.id })
    private val canonicalPayload = canonicalSignedPayload(id, version, source, this.entries)

    init {
        require(entries.isNotEmpty())
    }

    fun signedPayload(): CatalogSignedPayload = canonicalPayload

    companion object {
        fun computePayloadDigest(
            id: CatalogId,
            version: CatalogVersion,
            source: CatalogSource,
            entries: List<ProductCatalogEntry>,
        ): CatalogPayloadDigest = canonicalSignedPayload(id, version, source, entries.sortedBy { it.id }).digest
    }
}

fun interface CatalogSignatureVerifier {
    fun verify(
        payload: CatalogSignedPayload,
        publisherId: CatalogId,
        signature: CatalogSignature,
    ): Boolean
}

private fun canonicalSignedPayload(
    id: CatalogId,
    version: CatalogVersion,
    source: CatalogSource,
    entries: List<ProductCatalogEntry>,
): CatalogSignedPayload {
    val canonical = buildString {
        appendCanonical("codecks.catalog.bundle.v1")
        appendCanonical(id.value)
        appendVersion(version)
        when (source) {
            CatalogSource.Bundled -> appendCanonical("bundled")
            is CatalogSource.SignedPublisher -> {
                appendCanonical("signed_publisher")
                appendCanonical(source.publisherId.value)
            }
        }
        appendCanonical(entries.size.toString())
        entries.forEach { appendEntry(it) }
    }.toByteArray(Charsets.UTF_8)
    return CatalogSignedPayload(canonical.sha256Payload(), canonical)
}

internal fun ProductCatalogEntry.canonicalDigest(): CatalogPayloadDigest =
    buildString { appendEntry(this@canonicalDigest) }
        .toByteArray(Charsets.UTF_8)
        .sha256Payload()

private fun StringBuilder.appendEntry(entry: ProductCatalogEntry) {
    appendCanonical(
        when (entry) {
            is ProductCatalogEntry.Routine -> "routine"
            is ProductCatalogEntry.Theme -> "theme"
            is ProductCatalogEntry.SshPack -> "ssh_pack"
        },
    )
    appendCanonical(entry.id.value)
    appendCanonical(entry.title)
    appendCanonical(entry.summary)
    appendCanonical(entry.contentRevision.toString())
    appendCanonical(entry.accessClass.name)
    appendCompatibility(entry.compatibility)
    appendAccessibility(entry.accessibility)
    when (entry) {
        is ProductCatalogEntry.Routine -> {
            appendCanonical(entry.actionIds.size.toString())
            entry.actionIds.forEach { appendCanonical(it.value) }
        }
        is ProductCatalogEntry.Theme -> {
            appendCanonical(entry.presetId.value)
            appendCanonical(entry.previewColors.size.toString())
            entry.previewColors.forEach { appendCanonical(it) }
            appendCanonical(entry.motionIntensity.name)
            appendCanonical(entry.contrastClass.name)
        }
        is ProductCatalogEntry.SshPack -> {
            appendCanonical(entry.pack.id.value)
            appendVersion(entry.pack.version)
            appendCanonical(entry.pack.actions.size.toString())
            entry.pack.actions.forEach { action ->
                appendCanonical(action.id.value)
                appendCanonical(action.catalogActionId.value)
                appendCanonical(action.title)
            }
        }
    }
}

private fun StringBuilder.appendCompatibility(value: CatalogCompatibility) {
    appendCanonical(value.platform.name)
    appendCanonical(value.minimumAppVersionCode.toString())
    appendCanonical(value.maximumAppVersionCode?.toString() ?: "none")
    appendCanonical(value.minimumOsApiLevel.toString())
}

private fun StringBuilder.appendAccessibility(value: CatalogAccessibility) {
    appendCanonical(value.talkBackLabel)
    appendCanonical(value.minimumTouchTargetDp.toString())
    appendCanonical(value.minimumContrastRatio.toString())
    appendCanonical(value.supportsReducedMotion.toString())
    appendCanonical(value.colorIndependentMeaning.toString())
}

private fun StringBuilder.appendVersion(value: CatalogVersion) {
    appendCanonical(value.major.toString())
    appendCanonical(value.minor.toString())
    appendCanonical(value.patch.toString())
}

/** UTF-8 byte length prevents delimiter ambiguity for user-visible strings. */
private fun StringBuilder.appendCanonical(value: String) {
    append(value.toByteArray(Charsets.UTF_8).size)
    append(':')
    append(value)
    append('|')
}

private fun ByteArray.sha256Payload(): CatalogPayloadDigest = CatalogPayloadDigest(
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) },
)

enum class DiscoverySlotKind {
    RESERVED_AD,
}

data class ReservedDiscoverySlot(
    val id: CatalogId,
    val kind: DiscoverySlotKind = DiscoverySlotKind.RESERVED_AD,
    val afterOrganicItems: Int,
    val reservedHeightDp: Int,
    val shiftsContentWhenFilled: Boolean = false,
) {
    init {
        require(afterOrganicItems >= 6)
        require(reservedHeightDp >= 48)
        require(!shiftsContentWhenFilled)
    }
}

class CatalogDiscovery(
    entries: List<ProductCatalogEntry>,
    reservedSlots: List<ReservedDiscoverySlot>,
) {
    val entries: List<ProductCatalogEntry> = Collections.unmodifiableList(entries.toList())
    val reservedSlots: List<ReservedDiscoverySlot> = Collections.unmodifiableList(reservedSlots.toList())

    init {
        require(this.entries == this.entries.sortedBy { it.id })
        require(this.entries.map { it.id }.distinct().size == this.entries.size)
        require(this.reservedSlots == this.reservedSlots.sortedBy { it.id })
        require(this.reservedSlots.map { it.id }.distinct().size == this.reservedSlots.size)
        require(this.reservedSlots.all { it.afterOrganicItems <= this.entries.size })
    }
}

interface ProductCatalogRepository {
    /** Local and offline. No account/session is part of this contract. */
    fun explore(): CatalogDiscovery
    fun bundledBundle(): CatalogBundle
}

fun interface CatalogActionReferenceResolver {
    /** Resolves only stable IDs from the existing action catalog; it never returns runnable content. */
    fun contains(actionId: CatalogId): Boolean
}
