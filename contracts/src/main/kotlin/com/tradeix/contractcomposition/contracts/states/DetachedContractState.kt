package com.tradeix.contractcomposition.contracts.states

import com.tradeix.contractcomposition.contracts.DetachedContract
import com.tradeix.contractcomposition.contracts.common.FulfillableAsRefInput
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import com.tradeix.contractcomposition.contracts.common.StateRefOrRef
import net.corda.core.contracts.BelongsToContract

/**
 * This class holds a reference to a contract that we can say is detached. That is, this contract does not care that the
 * referenced contract is fulfilled in the same transaction as the one that fulfills this, but only that the contract
 * has already been fulfilled sometime prior. This is common among many basic contracts in society.
 *
 * For example, the employee at the entrance to a theater doesn't need to enforce that you pay him the price of entry
 * at the time entry - you can just as well use proof that you've paid beforehand in the form of a ticket.
 *
 * This class is the sister class of the [FulfilledContractsState]. Since the [FulfilledContractsState] can only be created
 * if the contract it references was in fact fulfilled in the same tx, our [DetachedContractState] can be sure the contract
 * it cares about was in fact fulfilled validly at some point in the past.
 *
 * A [CompositeContractState], for example, could require the execution of a [KeyControlState] & a [DetachedContractState].
 * This would mean that the composite contract could only be validly fulfilled if the key signed the transaction and a
 * [FulfilledContractsState] with proof of valid fulfillment of the correct other contract was present as a ref input.
 */

@BelongsToContract(DetachedContract::class)
data class DetachedContractState(
        val requiredFulfilledId: StateRefOrRef,
        override val participants: List<AbstractParty>
): ContractState, FulfillableAsRefInput