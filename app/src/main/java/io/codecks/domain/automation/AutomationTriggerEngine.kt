package io.codecks.domain.automation

data class AutomationTriggerEvaluation(
    val dueRecipes: List<AutomationRecipe>,
    val checkedCount: Int,
    val message: String,
    val checkedAtMillis: Long = 0L,
    val nextWindowStartAtMillis: Long = 0L,
    val nextWindowEndAtMillis: Long = 0L,
    val reasonByRecipeId: Map<String, String> = emptyMap(),
    val claimsByRecipeId: Map<String, AutomationTriggerClaim> = emptyMap(),
)

data class AutomationTriggerClaim(
    val recipeId: String,
    val recipeRevision: String,
    val fingerprint: String,
    val claimId: String,
    val leaseUntilMillis: Long,
)

interface AutomationTriggerEngine {
    suspend fun evaluate(recipes: List<AutomationRecipe>): AutomationTriggerEvaluation

    /**
     * Commits the durable trigger claim after execution reaches a terminal state.
     * An uncommitted claim remains eligible after process recreation.
     */
    suspend fun complete(claim: AutomationTriggerClaim): Boolean

    /**
     * Releases only the caller's exact running claim after a retryable pre-dispatch block.
     * A stale owner cannot release a replacement claim.
     */
    suspend fun release(claim: AutomationTriggerClaim): Boolean = false
}
