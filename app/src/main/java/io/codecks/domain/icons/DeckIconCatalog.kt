package io.codecks.domain.icons

import io.codecks.domain.ActionIcon
import java.util.Locale

private val SAFE_ID = Regex("[a-z][a-z0-9_.-]{2,63}")

@JvmInline
value class SemanticIconId(val value: String) : Comparable<SemanticIconId> {
    init { require(value.matches(SAFE_ID)) }
    override fun compareTo(other: SemanticIconId): Int = value.compareTo(other.value)
}

@JvmInline
value class IconPackId(val value: String) : Comparable<IconPackId> {
    init { require(value.matches(SAFE_ID)) }
    override fun compareTo(other: IconPackId): Int = value.compareTo(other.value)
}

enum class IconCategory { ALL, APPS, NAVIGATION, MEDIA, SYSTEM, CREATIVE, SAFETY }

data class IconLicense(
    val spdxId: String,
    val copyrightNotice: String,
    val upstreamUrl: String,
) {
    init {
        require(spdxId in setOf("MIT", "Apache-2.0"))
        require(copyrightNotice.isNotBlank())
        require(upstreamUrl.startsWith("https://"))
    }
}

data class IconPackDefinition(
    val id: IconPackId,
    val title: String,
    val license: IconLicense,
    val dependencyCoordinate: String,
    val rounded: Boolean,
) {
    init {
        require(title.isNotBlank())
        require(dependencyCoordinate.isNotBlank())
    }
}

data class SemanticIconDefinition(
    val id: SemanticIconId,
    val actionIcon: ActionIcon,
    val label: String,
    val category: IconCategory,
    val searchTerms: Set<String>,
) {
    init {
        require(label.isNotBlank())
        require(searchTerms.none(String::isBlank))
    }
}

data class IconPreferenceState(
    val favorites: List<SemanticIconId> = emptyList(),
    val recents: List<SemanticIconId> = emptyList(),
) {
    init {
        require(favorites.size <= MAX_FAVORITES)
        require(recents.size <= MAX_RECENTS)
        require(favorites.distinct().size == favorites.size)
        require(recents.distinct().size == recents.size)
    }

    fun toggleFavorite(id: SemanticIconId): IconPreferenceState = copy(
        favorites = if (id in favorites) favorites - id else (listOf(id) + favorites).take(MAX_FAVORITES),
    )

    fun recordRecent(id: SemanticIconId): IconPreferenceState = copy(
        recents = (listOf(id) + recents.filterNot { it == id }).take(MAX_RECENTS),
    )

    companion object {
        const val MAX_FAVORITES = 48
        const val MAX_RECENTS = 16
    }
}

class DeckIconCatalog(
    packs: List<IconPackDefinition>,
    icons: List<SemanticIconDefinition>,
) {
    val packs = packs.sortedBy { it.id }
    val icons = icons.sortedBy { it.id }
    private val byId = this.icons.associateBy { it.id }

    init {
        require(this.packs.map { it.id }.distinct().size == this.packs.size)
        require(this.icons.map { it.id }.distinct().size == this.icons.size)
        require(this.packs.size >= 4)
        require(SemanticIconId("icon.fallback") in byId)
    }

    fun find(id: SemanticIconId): SemanticIconDefinition = byId[id] ?: fallback()

    fun search(
        query: String,
        category: IconCategory = IconCategory.ALL,
        favoritesOnly: Boolean = false,
        preferences: IconPreferenceState = IconPreferenceState(),
    ): List<SemanticIconDefinition> {
        val needle = query.trim().lowercase(Locale.ROOT).take(MAX_QUERY)
        val favorites = preferences.favorites.toSet()
        return icons.filter { icon ->
            (!favoritesOnly || icon.id in favorites) &&
                (category == IconCategory.ALL || icon.category == category) &&
                (needle.isBlank() || icon.tokens().any { needle in it })
        }
    }

    fun recent(preferences: IconPreferenceState): List<SemanticIconDefinition> =
        preferences.recents.map(::find).distinctBy { it.id }

    private fun fallback(): SemanticIconDefinition =
        byId.getValue(SemanticIconId("icon.fallback"))

    private fun SemanticIconDefinition.tokens(): Set<String> =
        searchTerms.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }.apply {
            add(id.value)
            add(label.lowercase(Locale.ROOT))
            add(category.name.lowercase(Locale.ROOT))
            add(actionIcon.name.lowercase(Locale.ROOT))
        }

    private companion object { const val MAX_QUERY = 80 }
}
