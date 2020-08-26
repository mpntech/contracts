package com.tradeix.contractcomposition.contracts.common

import com.tradeix.contractcomposition.contracts.CompositeContract
import com.tradeix.contractcomposition.contracts.commands.FulfillRefInputsCommand
import com.tradeix.contractcomposition.contracts.states.CompositeContractState
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.transactions.LedgerTransaction

/**
 * A function to get a list of all the on-ledger contracts which are being fulfilled in a given tx
 * That is, those contracts which are in the inputs list directly and those in the ref inputs list and referenced by a
 * FulfillRefInputsCommand
 */
fun getAllFulfilledContracts(tx: LedgerTransaction): List<StateAndRef<*>> {
    val allFulfilledRefInputIndices = tx.commandsOfType<FulfillRefInputsCommand>().flatMap {
        it.value.fulfilledRefInputIndices
    }
    val allFulfilledRefInputs =
            if (allFulfilledRefInputIndices.isNotEmpty()) {
                tx.referenceInputRefsOfType<ContractState>().filterIndexed { index, _ -> index in allFulfilledRefInputIndices }
            } else listOf()

    return tx.inRefsOfType<ContractState>() + allFulfilledRefInputs
}

/**
 * A function to get all the contracts of some given type which are being fulfilled.
 * Verify methods needs this list in order to know which constraints to check.
 */
inline fun <reified T : FulfillRefInputsCommand, reified K: ContractState>
        getAllFulfilledContractsOfType(tx: LedgerTransaction): List<StateAndRef<K>> {
    val fulfilledRefInputIndicesOfType = tx.commandsOfType<T>().flatMap {
        it.value.fulfilledRefInputIndices
    }
    val allFulfilledRefInputsOfType =
            if (fulfilledRefInputIndicesOfType.isNotEmpty()) {
                tx.referenceInputRefsOfType<K>().filterIndexed { index, _ -> index in fulfilledRefInputIndicesOfType }
            } else listOf()

    return tx.inRefsOfType<K>() + allFulfilledRefInputsOfType
}