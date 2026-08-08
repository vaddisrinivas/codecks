package io.codecks.internalcommercial

import io.codecks.domain.commercial.BuildCapability
import io.codecks.domain.commercial.CommercialExecutionPolicy
import io.codecks.domain.commercial.CommercialGateState

/** Compile-time marker. Production artifact validation rejects this exact namespace. */
internal object CommercialTestOverrideMarker {
    const val COMPILED_IN = true

    /** Keeps capability selection in this source set; runtime input cannot choose the build gate. */
    fun policyFor(state: CommercialGateState): CommercialExecutionPolicy =
        CommercialExecutionPolicy.forCompiledVariant(
            state.copy(buildCapability = BuildCapability.INTERNAL_TEST_CAPABLE),
        )
}
