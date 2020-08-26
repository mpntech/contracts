package com.tradeix.contractcomposition.contracts.states

import com.tradeix.contractcomposition.contracts.KeyControlContract
import com.tradeix.contractcomposition.contracts.common.FulfillableAsRefInput
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

/**
 * This class represents the core contract in all Corda activity and, abstractly, the bridge from digital to physical worlds.
 * The ability to model assets, obligations, and rights generally in Corda stems from the fact that an actor needs to
 * physically control the private key associated with some public key in order to claim that they have the right to it.
 *
 * The control over a key is modelled as a [ContractState] such that we begin to have some coherency when developing new
 * contracts. Contracts are defined by a set or sets of other which contracts need to execute successfully in order for
 * the main contract to itself be considered fulfilled - signature requirements should be no different.
 *
 * All contracts eventually boil down to one or more [KeyControlState]s. The supporting logic is largely a matter of
 * determining which keys are required (e.g. A contract which requires a payment of some token doesn't care who signs
 * as long as that signature relinquishes control over the token; but we still need some [KeyControlState] to be executed).
 */

@BelongsToContract(KeyControlContract::class)
data class KeyControlState(
        val key: AbstractParty,
        override val participants: List<AbstractParty>
): ContractState, FulfillableAsRefInput