package com.tradeix.contractcomposition.contracts.states

import com.tradeix.contractcomposition.contracts.ArbitraryCompositeContract
import com.tradeix.contractcomposition.contracts.common.FulfillableAsRefInput
import com.tradeix.contractcomposition.contracts.common.StateRefOrRef
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

@BelongsToContract(ArbitraryCompositeContract::class)
class ArbitraryCompositeContractState(
        val data: String,
        val contracts: List<StateRefOrRef>,
        override val participants: List<AbstractParty>
): ContractState, FulfillableAsRefInput
