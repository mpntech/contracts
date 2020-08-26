package com.tradeix.contractcomposition.contracts.states

import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

data class TransactionGraphDescription(
        // Should be a list of some standard object which is able to locate some component in a transaction object graph
        val operands: List<Any>,
        val operators: (List<Any>) -> Boolean,
        override val participants: List<AbstractParty>
): ContractState

class TransactionGraphVerifier: Contract {
    override fun verify(tx: LedgerTransaction) {
        val graphOpinions = tx.inputsOfType<TransactionGraphDescription>()
        graphOpinions.forEach {
            require(it.operators(it.operands)) { "Graph must look the way all opinionated states want it to look" }
        }
    }
}