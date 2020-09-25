package com.tradeix.contractcomposition.contracts

import com.tradeix.contractcomposition.contracts.commands.FulfillRefInputsCommand
import com.tradeix.contractcomposition.contracts.common.getAllFulfilledContractsOfType
import com.tradeix.contractcomposition.contracts.states.DoubleSpendPreventionState
import net.corda.core.contracts.Contract
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class DoubleSpendPreventionContract : Contract {

    class FulfillDSPRefInputs(override val fulfilledRefInputIndices: List<Int>) : FulfillRefInputsCommand

    override fun verify(tx: LedgerTransaction) {
        if (tx.commandsOfType<FulfillDSPRefInputs>().isEmpty()) return

        val allFulfilledDSPContracts =
                getAllFulfilledContractsOfType<FulfillDSPRefInputs, DoubleSpendPreventionState>(tx)

        // All notarized states are just those in the inputs list of this transaction. This definition will change when
        // notarization becomes more generic (i.e. something like an obligation from notary not so sign again)8
        val allNotarizedStateRefs = tx.inputs.map { it.ref }

        //TODO: Maybe fix this to require the referenced contract is notarized if it is pointing to a contractyid and don't require notarization if state is already locked. If not here, then in amount controllers

        // Check that all the states which need to be notarized are included in the inputs list
        allFulfilledDSPContracts.forEach {
            for (contract in it.state.data.statesToNotarize) {
                val stateRef = if (contract.ref != null) StateRef(it.ref.txhash, contract.ref) else contract.stateRef
                requireThat { stateRef in allNotarizedStateRefs }
            }
        }
    }
}