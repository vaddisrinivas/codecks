package io.codecks.domain.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextAppRankerTest {
    @Test
    fun chromeContextRanksBrowserApps() {
        val ranked = ContextAppRanker.rank(
            snapshot = UserContextSnapshot(
                activeMacApp = "Google Chrome",
                macConnected = true,
                notificationSources = listOf("Gmail"),
                hourOfDay = 14,
            ),
            apps = listOf(
                ContextApp("com.android.settings", "Settings"),
                ContextApp("com.google.android.gm", "Gmail"),
                ContextApp("com.openai.chatgpt", "ChatGPT"),
            ),
        )

        assertEquals(
            listOf("com.google.android.gm", "com.openai.chatgpt"),
            ranked.map { it.app.packageName },
        )
        assertTrue(ranked.first().reason.contains("browser context"))
    }

    @Test
    fun promptListsOnlyAvailableAppsAndContextSignals() {
        val prompt = ContextAppPromptBuilder.build(
            snapshot = UserContextSnapshot(
                activeMacApp = "Terminal",
                macConnected = true,
                notificationSources = listOf("GitHub"),
                hourOfDay = 9,
            ),
            apps = listOf(ContextApp("com.github.android", "GitHub")),
        )

        assertTrue(prompt.contains("Mac app: Terminal"))
        assertTrue(prompt.contains("GitHub (com.github.android)"))
        assertTrue(prompt.contains("Return JSON only"))
    }

    @Test
    fun parserIgnoresAppsThatAreNotInstalled() {
        val ranked = ContextAppSuggestionParser.parse(
            payload = """
                {
                  "schemaVersion": 1,
                  "reason": "work",
                  "apps": [
                    {"packageName":"com.github.android","label":"GitHub","reason":"developer context"},
                    {"packageName":"com.fake.app","label":"Fake","reason":"invented"}
                  ]
                }
            """.trimIndent(),
            availableApps = listOf(ContextApp("com.github.android", "GitHub")),
        )

        assertEquals(1, ranked.size)
        assertEquals("com.github.android", ranked.single().app.packageName)
    }

    @Test
    fun notificationSourcesDoNotSuggestAppsByThemselves() {
        val ranked = ContextAppRanker.rank(
            snapshot = UserContextSnapshot(
                activeMacApp = null,
                macConnected = true,
                notificationSources = listOf("Airbnb", "Amazon Shopping", "Amex", "Gmail"),
                hourOfDay = 14,
            ),
            apps = listOf(
                ContextApp("com.airbnb.android", "Airbnb"),
                ContextApp("com.amazon.mShop.android.shopping", "Amazon Shopping"),
                ContextApp("com.americanexpress.android.acctsvcs.us", "Amex"),
                ContextApp("com.google.android.gm", "Gmail"),
            ),
        )

        assertTrue(ranked.isEmpty())
    }
}
