package io.codecks.data.catalog

import io.codecks.domain.catalog.LocalCatalogSnapshot
import io.codecks.domain.catalog.LocalCatalogStore

class InMemoryLocalCatalogStore(
    initial: LocalCatalogSnapshot = LocalCatalogSnapshot(0L, emptyMap(), emptyMap()),
) : LocalCatalogStore {
    private var current = initial

    @Synchronized
    override fun snapshot(): LocalCatalogSnapshot = current

    @Synchronized
    override fun replace(expectedRevision: Long, replacement: LocalCatalogSnapshot): Boolean {
        if (current.revision != expectedRevision || replacement.revision <= current.revision) return false
        current = replacement
        return true
    }
}
