package io.codecks.ui.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle

internal object ClipboardClipFactory {
    private const val GENERIC_LABEL = "Codecks clipboard sync"

    fun synchronizedPlainText(text: String): ClipData =
        ClipData.newPlainText(GENERIC_LABEL, text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(
                    clipboardSensitiveExtrasKey(Build.VERSION.SDK_INT) {
                        ClipDescription.EXTRA_IS_SENSITIVE
                    },
                    clipboardSystemPreviewMustBeHidden(),
                )
            }
        }
}
