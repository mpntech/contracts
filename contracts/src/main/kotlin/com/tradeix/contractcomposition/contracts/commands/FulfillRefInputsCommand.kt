package com.tradeix.contractcomposition.contracts.commands

import net.corda.core.contracts.CommandData
import com.tradeix.contractcomposition.contracts.common.FulfillableAsRefInput

//TODO: Let all contracts which need to be executed without being consumed implement this interface

/**
 * This is a simple interface which is used to mark those reference input contracts whose constraints the builder(s) of
 * the tx would like to claim they have met.
 *
 * This additional data is necessary because without it there would be no way for each verify function to know which
 * contracts are normal ref inputs and which require that the constraints in that contract are checked.
 *
 * Commands which implement this should provide the fulfilled indices only of the contract in which they're implemented.
 * The collection of these commands will provide any contract with the list of all fulfilled ref inputs in the tx.
 *
 * However, when using this list, contracts would be smart to check two things that may not be immediately intuitive.
 *
 * First, a contract should not consider a ref input fulfilled even if it is referenced by one of these commands if the
 * state in question does not implement [FulfillableAsRefInput]. This would mean that the state's contract has no idea
 * it should be doing any checks on any ref inputs regardless of whether it's referenced in some command! Obviously,
 * if the contract doesn't know it should enforce a contract's fulfillment requirements, it's not accurate to claim that
 * you've met them.
 *
 * Second, related to the first, even if a referenced state does implement [FulfillableAsRefInput], there is another way
 * contracts could pretend to be fulfilled without really having their constraints checked. That is, if there are no real
 * inputs or outputs for the contract type in question, the verify function for those states won't be run at all.
 * So, for each contract type referenced, verify() should ensure there is at least one input or output of that type.
 */
interface FulfillRefInputsCommand: CommandData {
    val fulfilledRefInputIndices: List<Int>
}