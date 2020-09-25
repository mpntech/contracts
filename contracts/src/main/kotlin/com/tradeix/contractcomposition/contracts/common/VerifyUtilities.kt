package com.tradeix.contractcomposition.contracts.common

import com.tradeix.contractcomposition.contracts.commands.FulfillRefInputsCommand
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.transactions.LedgerTransaction

/**
 * A function to get a list of all the on-ledger contracts which are being fulfilled in a given tx
 * That is, those contracts which are in the inputs list directly and those in the ref inputs list and referenced by a
 * FulfillRefInputsCommand
 */
fun getAllFulfilledContracts(tx: LedgerTransaction): List<StateAndRef<ContractState>>  = getAllFulfilledContractsOfType<FulfillRefInputsCommand, ContractState>(tx)

/**
 * A function to get all the contracts of some given type which are being fulfilled.
 * Verify methods needs this list in order to know which constraints to check.
 */
inline fun <reified T, reified K> getAllFulfilledContractsOfType(tx: LedgerTransaction): List<StateAndRef<K>>
        where T : FulfillRefInputsCommand, K : ContractState {

    val inputs = tx.referenceInputRefsOfType<K>()

    val allFulfilledRefInputsOfType = tx.commandsOfType<T>()
            .flatMap { it.value.fulfilledRefInputIndices }
            .distinct()
            .map { inputs[it] }

    return tx.inRefsOfType<K>() + allFulfilledRefInputsOfType
}

