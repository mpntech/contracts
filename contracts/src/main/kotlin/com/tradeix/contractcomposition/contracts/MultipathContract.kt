package com.tradeix.contractcomposition.contracts

import com.tradeix.contractcomposition.contracts.commands.FulfillRefInputsCommand
import com.tradeix.contractcomposition.contracts.common.getAllFulfilledContracts
import com.tradeix.contractcomposition.contracts.common.getAllFulfilledContractsOfType
import com.tradeix.contractcomposition.contracts.states.CompositeContractState
import com.tradeix.contractcomposition.contracts.states.MultipathContractState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import javax.security.auth.login.CredentialException


/**
 * Unlike the [CompositeContract], we will not need to extend [MultipathContract] functionality. Instead, composite
 * contracts or extensions thereof will simply always point to one or more [MultipathContractState]s if they would like
 * to branch their execution logic (e.g. collateral as part of an obligation will have multiple execution options)
 */

class MultipathContract: Contract {

    interface MultipathCommands : CommandData

    /**
     * For each MultipathContract being fulfilled (including those fulfilled as normal inputs), we will use this command
     * to inform the verify function below which of the paths we are attempting to take so we don't require that verify
     * check all of them arbitrarily until they find one that works (there may be loads in future!)
     */
    data class FulfillMultipathContracts(
            override val fulfilledRefInputIndices: List<Int>,
            val fulfilledPaths: Map<StateRef, Int> = mapOf()) : FulfillRefInputsCommand, MultipathCommands

    override fun verify(tx: LedgerTransaction) {

        if (tx.commandsOfType<FulfillMultipathContracts>().isEmpty()) return

        // We'll assume there's only one Multipath command for now as I don't want to google how to merge maps...
        // Probably not a terrible assumption until we want multiple parties to build up a transaction together
        val fulfilledPaths = tx.commandsOfType<FulfillMultipathContracts>().single().value.fulfilledPaths

        val allFulfilledMultipathContracts =
                getAllFulfilledContractsOfType<FulfillMultipathContracts, MultipathContractState>(tx)
        val allFulfilledContractRefs = getAllFulfilledContracts(tx).map { it.ref }

        // Check that for each fulfilled Multipath, there is a path specified and the contract in that path is fulfilled
        allFulfilledMultipathContracts.forEach {
            val takenPath = fulfilledPaths[it.ref]
                    ?: error("No path provided for fulfilled Multipath with StateRef ${it.ref}")
            val contract = it.state.data.contracts[takenPath]
            if (contract.ref == null) {
                requireThat { contract.stateRef in allFulfilledContractRefs }
            } else {
                val stateRefFromRef = StateRef(it.ref.txhash, contract.ref)
                requireThat { stateRefFromRef in allFulfilledContractRefs }
            }
        }
    }
}

val x = {y: List<Any> -> ((y[0] as Int > y[1] as Int))}