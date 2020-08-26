package com.tradeix.contractcomposition.contracts.states

import com.tradeix.contractcomposition.contracts.FulfilledContractsStateContract
import com.tradeix.contractcomposition.contracts.common.StateRefOrRef
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.identity.AbstractParty

/**
 * This state is only created and never spent.
 * It can be created if the contracts it claims are fulfilled are in fact fulfilled in the transaction which creates it.
 * This is helpful as contracts often don't care as much about the set of other contracts fulfilled in the transaction
 * as much as they care that those contracts have already been fulfilled
 * (e.g. I don't care who comes to my birthday party as long as they can prove they gave me 10,000 satoshi).
 *
 * This state is the sister class of [DetachedContractState]. Any fulfilled [FulfilledContractsState] can fulfill a
 * [DetachedContractState] as long as it was created alongside fulfillment of the required contract (by StateRef).
 */

@BelongsToContract(FulfilledContractsStateContract::class)
data class FulfilledContractsState(
        val fulfilledContracts: List<StateRef>,
        override val participants: List<AbstractParty>): ContractState