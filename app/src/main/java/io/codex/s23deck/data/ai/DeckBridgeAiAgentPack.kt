package io.codex.s23deck.data.ai

import android.content.Context

data class DeckBridgeAiAgentPack(
    val agent: String,
    val skill: String,
    val schema: String,
) {
    val prompt: String = buildString {
        appendLine("# Bundled Codecks AI Agent")
        appendLine(agent.trim())
        appendLine()
        appendLine("# Bundled Skill")
        appendLine(skill.trim())
        appendLine()
        appendLine("# Bundled Schema")
        appendLine(schema.trim())
    }

    companion object {
        fun load(context: Context): DeckBridgeAiAgentPack =
            DeckBridgeAiAgentPack(
                agent = context.readAgentAsset("deckbridge_ai_agent/AGENT.md"),
                skill = context.readAgentAsset("deckbridge_ai_agent/SKILL.md"),
                schema = context.readAgentAsset("deckbridge_ai_agent/schema.json"),
            )
    }
}

private fun Context.readAgentAsset(path: String): String =
    runCatching {
        assets.open(path).bufferedReader().use { it.readText() }
    }.getOrElse {
        "Codecks bundled AI agent asset missing: $path"
    }
