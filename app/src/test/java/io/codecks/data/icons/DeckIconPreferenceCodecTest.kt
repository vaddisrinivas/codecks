package io.codecks.data.icons

import io.codecks.domain.icons.IconPreferenceState
import io.codecks.domain.icons.SemanticIconId
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckIconPreferenceCodecTest {
    @Test
    fun roundTripIsDeterministicAndPreservesOrder() {
        val state = IconPreferenceState(
            favorites = listOf(SemanticIconId("icon.terminal"), SemanticIconId("icon.search")),
            recents = listOf(SemanticIconId("icon.play")),
        )
        val encoded = DeckIconPreferenceCodec.encode(state)
        assertEquals(encoded, DeckIconPreferenceCodec.encode(DeckIconPreferenceCodec.decode(encoded)))
        assertEquals(state, DeckIconPreferenceCodec.decode(encoded))
    }

    @Test
    fun corruptFutureAndOversizedStateFailClosed() {
        assertEquals(IconPreferenceState(), DeckIconPreferenceCodec.decode("v2\nicon.terminal\nicon.play"))
        assertEquals(IconPreferenceState(), DeckIconPreferenceCodec.decode("v1\nnot valid!\nicon.play"))
        assertEquals(IconPreferenceState(), DeckIconPreferenceCodec.decode("x".repeat(8 * 1024 + 1)))
    }
}
