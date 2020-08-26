package com.tradeix.contractcomposition.contracts.states

import com.tradeix.contractcomposition.contracts.FulfilledByNewContractContract
import com.tradeix.contractcomposition.contracts.common.FulfillableAsRefInput
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

/**
 * This class represents a contract which can only be fulfilled if a certain new contract (an output) is minted in the
 * tx which is attempting to fulfill this one. This is another very common requirement in basic contracts.
 *
 * For example, to buy a ticket, a theater doesn't want proof that you CAN use some cash (i.e. fulfill another contract),
 * it wants you to use that ability to create a new output of the "cash" such that it can now be used by the theater.
 * This implies that you're able to use the cash, of course, but it's not enough to prove you have that right, you need
 * to create a new contract which hands that right to the theater.
 *
 * This will need to change...now should not be a ContractState but rather just some state data that will go in our
 * base DifferentiatedState and then the contracts that should be run to make sure it is a valid one
 *
 */

@BelongsToContract(FulfilledByNewContractContract::class)
class FulfilledByNewContract(
        val newContract: ContractState,
        override val participants: List<AbstractParty>): ContractState, FulfillableAsRefInput