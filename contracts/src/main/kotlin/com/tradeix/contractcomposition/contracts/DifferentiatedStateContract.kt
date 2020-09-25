package com.tradeix.contractcomposition.contracts

import com.tradeix.contractcomposition.contracts.commands.FulfillRefInputsCommand
import com.tradeix.contractcomposition.contracts.common.getAllFulfilledContracts
import com.tradeix.contractcomposition.contracts.common.getAllFulfilledContractsOfType
import com.tradeix.contractcomposition.contracts.states.DifferentiatedState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

/**
 * This class is almost identical to the CompositeContract.
 *
 * The only difference is that we need to check only that each state's single contract is in the overall list of
 * fulfilled contracts since there only can be a single one.
 */

class DifferentiatedStateContract : Contract {

    interface DifferentiatedContractCommands : CommandData

    data class FulfillDifferentiatedContracts(override val fulfilledRefInputIndices: List<Int> = listOf()) : FulfillRefInputsCommand, DifferentiatedContractCommands

    override fun verify(tx: LedgerTransaction) {

        if (tx.commandsOfType<FulfillDifferentiatedContracts>().isEmpty()) return

        val allFulfilledDifferentiatedContracts =
                getAllFulfilledContractsOfType<FulfillDifferentiatedContracts, DifferentiatedState<*>>(tx)

        val allFulfilledContractRefs = getAllFulfilledContracts(tx).map { it.ref }

        allFulfilledDifferentiatedContracts.forEach {
            val contract = it.state.data.contract

            val stateRef = if (contract.ref != null) StateRef(it.ref.txhash, contract.ref) else contract.stateRef

            requireThat { stateRef in allFulfilledContractRefs }
        }
    }
}
