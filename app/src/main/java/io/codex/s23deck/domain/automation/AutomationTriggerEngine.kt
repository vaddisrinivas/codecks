package io.codex.s23deck.domain.automation

data class AutomationTriggerEvaluation(
    val dueRecipes: List<AutomationRecipe>,
    val checkedCount: Int,
    val message: String,
)

interface AutomationTriggerEngine {
    suspend fun evaluate(recipes: List<AutomationRecipe>): AutomationTriggerEvaluation
}
