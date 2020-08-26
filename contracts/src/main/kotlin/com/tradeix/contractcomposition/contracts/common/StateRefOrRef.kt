package com.tradeix.contractcomposition.contracts.common

import net.corda.core.contracts.StateRef
import net.corda.core.serialization.CordaSerializable

/**
 * Basic data class to allow a state to reference another in the same way regardless of whether it was created in some
 * previous transaction or is being created at the same time as the one referencing it.
 *
 * If the referenced state is created at the same time, its [StateRef] can be produced later by taking the txhash from
 * the referencing state and using the ref property to set its output index.
 */
@CordaSerializable
data class StateRefOrRef (val stateRef: StateRef? = null, val ref: Int? = null)