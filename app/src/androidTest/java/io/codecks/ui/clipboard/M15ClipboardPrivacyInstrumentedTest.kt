package io.codecks.ui.clipboard

import android.content.ClipDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M15ClipboardPrivacyInstrumentedTest {
    @Test
    fun ordinarySynchronizedClipCarriesSensitivePreviewFlag() {
        val clip = ClipboardClipFactory.synchronizedPlainText("ordinary clipboard text")

        assertTrue(clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true)
    }

    @Test
    fun synchronizedClipDescriptionUsesGenericLabel() {
        val clip = ClipboardClipFactory.synchronizedPlainText("private value")

        assertEquals("Codecks clipboard sync", clip.description.label.toString())
    }
}
