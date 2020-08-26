package com.tradeix.contractcomposition.contracts.states

import com.tradeix.contractcomposition.contracts.common.FulfillableAsRefInput
import com.tradeix.contractcomposition.contracts.common.StateRefOrRef
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

/**
 * This is the base state in our contract composition model.
 *
 * The state may contain any data and points to a single on-ledger contract by output index or StateRef. After creation,
 * this contract state can only be fulfilled in a subsequent transaction if the contract it points to is also fulfilled.
 *
 * By default, there is no logic when this state is created. However, other contracts which are being fulfilled in the
 * transaction which creates this one may and often will have an opinion about what data this state can contain.
 * For example, if T has an amount field, that amount can be any number. But, if an AmountController contract is added
 * to and fulfilled in the same transaction which is creating the new, amount-populated state, then the AmountController
 * may enforce something like "states with amounts cannot together have a sum greater than the sum of input amounts."
 *
 * This explicit decoupling of data and the checks which ran is helpful in that it more intuitively allows developers to regulate the creation of arbitrary states
 * with more than one contract. This should mean that we duplicate logic less because we don't need to change a contract
 * class when we change some state, we can simply create another contract which will also be used to verify that data.
 *
 * Some T could implement an Amount interface and an ID interface, as a simple example, and both the contract which
 * cares about states with amounts and that which cares about states with IDs could be roped into the transaction to
 * offer their opinions about whether that state with an amount and an ID ought to be allowed to exist.
 *
 * Also important is that a single verify function could allow the state types it is in charge of watching over to
 * change shape through stages of evolution. A state which represents the right of an obligee to collect on incoming
 * payments, for example, may not need to have an amount (which could represent the % of incoming funds the owner of
 * that state is able to claim) until it splits.
 */

data class DifferentiatedState<T: Any>(
        val data: T,
        val contract: StateRefOrRef,
        override val participants: List<AbstractParty>
): ContractState, FulfillableAsRefInput