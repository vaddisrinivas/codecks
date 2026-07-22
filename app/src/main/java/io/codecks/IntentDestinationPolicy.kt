package io.codecks

import android.content.Context
import android.content.Intent
import java.util.UUID

object InternalIntentAuth {
    const val EXTRA_TOKEN = "io.codecks.INTERNAL_DESTINATION_TOKEN"
    const val ACTION_DEBUG_OPEN_DESTINATION = "io.codecks.DEBUG_OPEN_DESTINATION"

    fun sign(context: Context, intent: Intent): Intent =
        intent.putExtra(EXTRA_TOKEN, token(context))

    fun token(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_TOKEN, null)?.let { return it }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_TOKEN, created).apply()
        return created
    }

    private const val PREFS_NAME = "codecks.internal_intents"
    private const val KEY_TOKEN = "destination_token"
}

internal fun resolveDestinationRequest(
    action: String?,
    type: String?,
    destination: String?,
    providedToken: String?,
    expectedToken: String,
): String? =
    when {
        action == Intent.ACTION_SEND && type == "text/plain" -> "ai"
        destination.isNullOrBlank() -> null
        providedToken == expectedToken -> destination
        BuildConfig.DEBUG && action == InternalIntentAuth.ACTION_DEBUG_OPEN_DESTINATION -> destination
        else -> null
    }
