package io.codecks.ui.icons

import io.codecks.domain.ActionIcon
import io.codecks.ui.theme.CodecksIconPack
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test

class RoundedDeckIconResolverTest {
    @Test
    fun everySemanticIconResolvesInEveryPack() {
        CodecksIconPack.entries.forEach { pack ->
            ActionIcon.entries.forEach { icon -> assertNotNull(icon.imageVector(pack)) }
        }
    }

    @Test
    fun creativeSemanticIconsDoNotCollapseToOneGlyph() {
        CodecksIconPack.entries.forEach { pack ->
            val names = listOf(ActionIcon.Party, ActionIcon.Sparkle, ActionIcon.Emoji)
                .map { it.imageVector(pack).name }
            assertEquals("$pack creative mappings", 3, names.distinct().size)
        }
    }
}
