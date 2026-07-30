package io.codecks.domain.automation

data class AutomationTriggerEvaluation(
    val dueRecipes: List<AutomationRecipe>,
    val checkedCount: Int,
    val message: String,
    val checkedAtMillis: Long = 0L,
    val nextWindowStartAtMillis: Long = 0L,
    val nextWindowEndAtMillis: Long = 0L,
    val reasonByRecipeId: Map<String, String> = emptyMap(),
)

interface AutomationTriggerEngine {
    suspend fun evaluate(recipes: List<AutomationRecipe>): AutomationTriggerEvaluation
}
