package com.tradeix.contractcomposition.contracts

import com.tradeix.contractcomposition.contracts.commands.FulfillRefInputsCommand
import com.tradeix.contractcomposition.contracts.common.getAllFulfilledContractsOfType
import com.tradeix.contractcomposition.contracts.states.KeyControlState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireThat
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

/**
 * This is a simple class to ensure that any transaction on the network which claims a KeyControlState is being fulfilled
 * (by nature of the fact that such a state is in the inputs list or reference by a [Fulfill] command) has a signature
 * over it which proves ownership of the private key associate with the public key contained in the state.
 */

class KeyControlContract: Contract {

    interface KeyControlCommands : CommandData

    class FulfillKeyControlContracts(override val fulfilledRefInputIndices: List<Int>) : KeyControlCommands, FulfillRefInputsCommand

    /**
     * To verify control of a private key, our verify function will simply check that the key is included in the list of
     * required signers in the transaction's commands. Corda handles the actual signature verification for us.
     */
    override fun verify(tx: LedgerTransaction) {
        if (tx.commandsOfType<FulfillKeyControlContracts>().isEmpty()) return

        val signers = tx.commands.flatMap { it.signers }

        val allFulfilledKeyControlStates = getAllFulfilledContractsOfType<FulfillKeyControlContracts, KeyControlState>(tx)

        require(allFulfilledKeyControlStates.all { it.state.data.key.owningKey in signers })
    }
}