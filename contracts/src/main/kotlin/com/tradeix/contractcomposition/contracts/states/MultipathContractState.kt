package com.tradeix.contractcomposition.contracts.states

import com.tradeix.contractcomposition.contracts.MultipathContract
import com.tradeix.contractcomposition.contracts.common.FulfillableAsRefInput
import com.tradeix.contractcomposition.contracts.common.StateRefOrRef
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

/**
 * This class is our building block for OR logic in contractual composition. Rather than specify all the contracts which
 * must also be executed in order for itself to be used, the [MultipathContractState] specifies options. At least one
 * contract in its list of contracts must be executed in the same transaction. We say at least one because a party is
 * welcome to fulfill additional contracts in the tx which would satisfy this contract, but they won't gain anything as
 * far as this one is concerned.
 */

@BelongsToContract(MultipathContract::class)
data class MultipathContractState(
        val contracts: List<StateRefOrRef>,
        override val participants: List<AbstractParty>
): ContractState, FulfillableAsRefInput