package io.codex.s23deck

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.codex.s23deck.data.context.ContextDeckInteractionStore

class WidgetLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val destination = intent.getStringExtra(EXTRA_DESTINATION).orEmpty().ifBlank { "context" }

        if (packageName.isNotBlank()) {
            ContextDeckInteractionStore.record(
                context = this,
                type = "widget_app_launch",
                target = packageName,
                label = label.ifBlank { packageName },
            )
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
                return
            }
        } else {
            ContextDeckInteractionStore.record(
                context = this,
                type = "widget_open_context",
                target = destination,
                label = "Context Deck",
            )
        }

        startActivity(
            InternalIntentAuth.sign(
                this,
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_DESTINATION, destination)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            ),
        )
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "io.codex.s23deck.widget.PACKAGE_NAME"
        const val EXTRA_LABEL = "io.codex.s23deck.widget.LABEL"
        const val EXTRA_DESTINATION = "io.codex.s23deck.widget.DESTINATION"
    }
}
