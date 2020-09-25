package com.tradeix.contractcomposition.contracts

import com.tradeix.contractcomposition.contracts.commands.FulfillRefInputsCommand
import com.tradeix.contractcomposition.contracts.common.StateRefOrRef
import com.tradeix.contractcomposition.contracts.common.getAllFulfilledContractsOfType
import com.tradeix.contractcomposition.contracts.states.CompositeContractState
import com.tradeix.contractcomposition.contracts.states.DetachedContractState
import com.tradeix.contractcomposition.contracts.states.FulfilledContractsState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

/**
 * This class ensures that, for detached contract states, there is proof that the referenced contract was already
 * successfully fulfilled in a previous transaction.
 *
 * To do so, the contract simply requires that a [FulfilledContractsState] which contains the required [StateRef] to be
 * present as a reference input in the transaction which is fulfilling the [DetachedContractState].
 */

class DetachedContract: Contract {

    interface DetachedContractCommands : CommandData

    class FulfillDetachedContracts(override val fulfilledRefInputIndices: List<Int>) : FulfillRefInputsCommand, DetachedContractCommands

    override fun verify(tx: LedgerTransaction) {

        if (tx.commandsOfType<FulfillDetachedContracts>().isEmpty()) return

        val allFulfilledDetachedContracts =
                getAllFulfilledContractsOfType<FulfillDetachedContracts, DetachedContractState>(tx)

        // All those contracts for which we have proof here have already been fulfilled in some other transaction
        val previouslyFulfilledContractRefs = tx.referenceInputRefsOfType<FulfilledContractsState>()
                .flatMap { it.state.data.fulfilledContracts }

        require(allFulfilledDetachedContracts.all { it.state.data.requiredFulfilledId.stateRef in previouslyFulfilledContractRefs })
    }
}