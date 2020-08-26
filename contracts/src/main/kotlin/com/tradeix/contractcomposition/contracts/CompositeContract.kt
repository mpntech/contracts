package com.tradeix.contractcomposition.contracts

import com.tradeix.contractcomposition.contracts.commands.FulfillRefInputsCommand
import com.tradeix.contractcomposition.contracts.common.getAllFulfilledContracts
import com.tradeix.contractcomposition.contracts.common.getAllFulfilledContractsOfType
import com.tradeix.contractcomposition.contracts.states.CompositeContractState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

/**
 * This is a contract which simply enforces that contracts which are compositions of others (as most are) can
 * only be considered validly fulfilled if the contracts which comprise them are fulfilled in the same transaction.
 * Those other contracts can be referenced by StateRef if they exist prior to this transaction or by output index
 * if they are being created here.
 */

open class CompositeContract: Contract {

    interface CompositeContractCommands : CommandData

    data class FulfillCompositeContracts(override val fulfilledRefInputIndices: List<Int> = listOf()) : FulfillRefInputsCommand, CompositeContractCommands

    /**
     * The verify function of this contract should be as simple as follows: For each composite contract state which
     * the transaction claims is being fulfilled, ensure that the contracts which comprise that state are also present
     * in the list of fulfilled contracts.
     *
     * In the existing Corda data model, however, we don't have a single list of contracts we are fulfilling in a tx.
     * Instead, nodes run the contracts of all inputs and all outputs. Although we could put all fulfilled contracts in
     * the list of inputs, we often want to prove fulfillment of an existing contract without requiring that a notary
     * prevent that contract from being used again. So, we'll need to place those contracts which we're fulfilling but
     * not consuming in the list of reference inputs. Therefore, we can't simply look in the inputs list to find those
     * contracts we need to check in this verify function, we also need to look at the reference inputs.
     *
     * However, since not all reference inputs are being fulfilled, we will look at only those which the
     * [FulfillCompositeContracts] claims we are trying to fulfill (by pointing to a subset of ref inputs by index).
     *
     * Contracts which require other contracts to be in the "list" of fulfilled contracts in the same transaction really
     * demand, then, that those contracts are in either the list of inputs directly or in the list of ref inputs and
     * explicitly referenced by the CompositeContract Fulfill command in the tx.
     */
    final override fun verify(tx: LedgerTransaction) {

        if (tx.commandsOfType<FulfillCompositeContracts>().isEmpty()) return

        val allFulfilledCompositeContracts =
                getAllFulfilledContractsOfType<FulfillCompositeContracts, CompositeContractState>(tx)
        val allFulfilledContractRefs = getAllFulfilledContracts(tx).map { it.ref }

        allFulfilledCompositeContracts.forEach {
            for (contract in it.state.data.contracts) {
                if (contract.ref == null) {
                    requireThat { contract.stateRef in allFulfilledContractRefs }
                } else {
                    val stateRefFromRef = StateRef(it.ref.txhash, contract.ref)
                    requireThat { stateRefFromRef in allFulfilledContractRefs }
                }
            }
        }
    }
}