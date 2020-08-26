package com.tradeix.contractcomposition.contracts.states

import com.tradeix.contractcomposition.contracts.DoubleSpendPreventionContract
import com.tradeix.contractcomposition.contracts.common.FulfillableAsRefInput
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import com.tradeix.contractcomposition.contracts.common.StateRefOrRef
import net.corda.core.contracts.BelongsToContract

/**
 * This class is a mostly bespoke implementation to help us configure our "ContractManagers" to act appropriately.
 * It is largely a placeholder until we can design a more generic mechanism to model notarization.
 * Notarization should eventually look much the same as all the other rights we represent. Getting a state notarized
 * really means acquiring the right to hold some party accountable (either on- or off-chain) if they go on to sign
 * another transaction with that same state as one of the contracts in the "inputs" list.
 *
 * For now, however, we can represent the need for notarization by a simple [DoubleSpendPreventionState] which contains
 * a list of the contracts which need to be either A.) consumed as inputs in a tx which fulfills the [DoubleSpendPreventionState]
 * OR B.) already locked in such a way that they are effectively notarized (more on this in contract class description...)
 *
 * A [CompositeContractState] which could only ever be fulfilled once, for example, would need to point to one of these
 * [DoubleSpendPreventionState]s which would itself reference the composite contract (by output ref rather than stateref
 * since they would be created at the same time) to force that the composite contract get consumed when it is fulfilled.
 *
 * As should be obvious, any contracts which do not require execution of any [DoubleSpendPreventionState]s can be spent
 * any number of times that the contract is met. This is by design and allows for more flexible on-ledger contracts.
 */

@BelongsToContract(DoubleSpendPreventionContract::class)
data class DoubleSpendPreventionState(
        val statesToNotarize: List<StateRefOrRef>,
        override val participants: List<AbstractParty>
): ContractState, FulfillableAsRefInput