package com.tradeix.contractcomposition.contracts

import com.tradeix.contractcomposition.contracts.common.getAllFulfilledContracts
import com.tradeix.contractcomposition.contracts.states.CompositeContractState
import com.tradeix.contractcomposition.contracts.states.FulfilledContractsState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.StateRef
import net.corda.core.transactions.LedgerTransaction

/**
 *
 */

class FulfilledContractsStateContract: Contract {

    class CreateFulfilledContractsState: CommandData

    override fun verify(tx: LedgerTransaction) {

        val allFulfilledContractRefs = getAllFulfilledContracts(tx).map { it.ref }

        // Does this work? Trying to get all the FulfilledContractsState outputs from list of all the outputs
        val newFulfilledContracts = tx.outputs.filter {
            it.javaClass.isInstance(FulfilledContractsState::class) } as List<FulfilledContractsState>
        newFulfilledContracts.forEach {
            it.fulfilledContracts.forEach {
                require(allFulfilledContractRefs.contains(it))
            }
        }
    }
}