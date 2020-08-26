package com.tradeix.contractcomposition.contracts.states

import com.tradeix.contractcomposition.contracts.CompositeContract
import com.tradeix.contractcomposition.contracts.common.FulfillableAsRefInput
import com.tradeix.contractcomposition.contracts.common.StateRefOrRef
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

/**
 * This class represents our core building block for more complex logic. It is the AND operator for contract composition.
 * That is, the [CompositeContractState] contains a list of other static contracts which must also be fulfilled for this
 * contract itself to be validly fulfilled in any given transaction.
 *
 * The contracts it points to are static in that they are fully-formed states on ledger and can only act to fulfill this
 * contract in that precise position (i.e. with that exact StateRef, not a descendant created after a state evolution).
 * Other contracts will handle the case where we want the required contract to be able to evolve...this state does not.
 * In other words, the composite contract does not say "those state or their successors need to be fulfilled in any
 * transaction which fulfills me" but rather only "those states and those states specifically need to be fulfilled in
 * any transaction which fulfills me."
 *
 * This contract also does not support the case where contracts need to be fulfilled but can be fulfilled in a
 * transaction prior to the fulfillment of this one. Although a common contractual construct, that too will be dealt
 * with elsewhere (see: DetachedContractState).
 *
 */

@BelongsToContract(CompositeContract::class)
data class CompositeContractState (
        val contracts: List<StateRefOrRef>,
        override val participants: List<AbstractParty>): ContractState, FulfillableAsRefInput