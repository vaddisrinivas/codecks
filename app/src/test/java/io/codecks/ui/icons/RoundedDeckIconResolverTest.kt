package io.codecks.ui.icons

import io.codecks.domain.ActionIcon
import io.codecks.ui.theme.CodecksIconPack
import org.junit.Assert.assertNotNull
import org.junit.Test

class RoundedDeckIconResolverTest {
    @Test
    fun everySemanticIconResolvesInEveryPack() {
        CodecksIconPack.entries.forEach { pack ->
            ActionIcon.entries.forEach { icon -> assertNotNull(icon.imageVector(pack)) }
        }
    }
}
