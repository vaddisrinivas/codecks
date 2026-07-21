package io.codecks.domain.deck

data class LegacyDeckImportResult(
    val slotCount: Int,
    val actionIds: List<String?>,
    val unknownIds: List<String>,
) {
    val importedCount: Int = actionIds.count { it != null }
}

object LegacyDeckLayoutImporter {
    val supportedSlotCounts = setOf(24, 32, 64)

    fun importActionIds(
        rawLayout: String,
        availableActionIds: Set<String>,
        requestedSlotCount: Int? = null,
    ): LegacyDeckImportResult {
        val candidates = extractCandidates(rawLayout)
        val slotCount = requestedSlotCount?.takeIf(::isSupportedSlotCount)
            ?: inferSlotCount(rawLayout, candidates.size)
        val knownIds = availableActionIds.map { it.lowercase() }.toSet()
        val imported = mutableListOf<String?>()
        val unknown = linkedSetOf<String>()

        for (candidate in candidates) {
            if (imported.size == slotCount) break
            val id = normalize(candidate)
            when {
                id == null -> imported += null
                id in knownIds -> imported += id
                isStructuralToken(id) -> Unit
                looksLikeActionId(id) -> {
                    imported += null
                    unknown += id
                }
            }
        }

        return LegacyDeckImportResult(
            slotCount = slotCount,
            actionIds = imported.padTo(slotCount),
            unknownIds = unknown.toList(),
        )
    }

    private fun inferSlotCount(rawLayout: String, candidateCount: Int): Int {
        val declared = Regex("""\b(24|32|64)\b""")
            .findAll(rawLayout)
            .mapNotNull { it.value.toIntOrNull() }
            .firstOrNull(::isSupportedSlotCount)
        if (declared != null) return declared

        return supportedSlotCounts.firstOrNull { candidateCount <= it } ?: 64
    }

    private fun isSupportedSlotCount(slotCount: Int): Boolean = slotCount in supportedSlotCounts

    private fun extractCandidates(rawLayout: String): List<String> {
        val keyed = Regex(
            pattern = """["']?(?:actionId|buttonId|slotId|id)["']?\s*[:=]\s*["']?([A-Za-z0-9_./:-]+|null|none|empty|blank)""",
            options = setOf(RegexOption.IGNORE_CASE),
        ).findAll(rawLayout).map { it.groupValues[1] }.toList()
        if (keyed.isNotEmpty()) return keyed

        return Regex("""["']?([A-Za-z][A-Za-z0-9_./:-]*|null|none|empty|blank)["']?""")
            .findAll(rawLayout)
            .map { it.groupValues[1] }
            .toList()
    }

    private fun normalize(token: String): String? {
        val normalized = token.trim()
            .trim('"', '\'', '[', ']', '{', '}', ',', ';')
            .lowercase()
        return when (normalized) {
            "", "null", "none", "empty", "blank" -> null
            "add" -> "add_button"
            "volume_up" -> "vol_up"
            "volume_down" -> "vol_down"
            "playpause" -> "play_pause"
            else -> normalized
        }
    }

    private fun isStructuralToken(token: String): Boolean = token in setOf(
        "actions", "action", "buttons", "button", "deck", "layout", "profile", "slots", "slot", "ids",
        "id", "label", "name", "kind", "command", "route", "rows", "cols", "columns", "size",
    )

    private fun looksLikeActionId(token: String): Boolean =
        token.any { it == '_' || it == '-' || it.isDigit() } || token.length >= 3

    private fun List<String?>.padTo(slotCount: Int): List<String?> =
        if (size >= slotCount) take(slotCount) else this + List(slotCount - size) { null }
}
