package com.tradeix.contractcomposition.contracts

import com.tradeix.contractcomposition.contracts.states.DummyPaymentState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

/**
 * A [DummyPaymentState] is just a claim from one party that they paid a certain amount of some currency to another party.
 * So, all our [DummyPaymentContract] will need to check is that the party the state says is making the claim of off-chain
 * payment (i.e. the "payor") must sign the transaction which creates that claim.
 *
 * We don't care about amounts or anything else. If the payor wants to claim they made a payment of a negative value,
 * they may. Whether or not such a claim would be helpful in any capacity is determined by other states and contracts
 */

class DummyPaymentContract: Contract {

    override fun verify(tx: LedgerTransaction) {
        // DummyPaymentStates can only ever be created so we don't need to do any checks on commands or their types

        // The party who claims payment has been made (the "payor") must sign the transaction to make that claim.
        val signers = tx.commands.flatMap { it.signers }
        tx.outputsOfType<DummyPaymentState>().forEach {
            it.payor.owningKey in signers
        }
    }

}