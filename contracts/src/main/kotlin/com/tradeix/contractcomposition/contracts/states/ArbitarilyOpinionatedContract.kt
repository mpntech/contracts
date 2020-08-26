package com.tradeix.contractcomposition.contracts.states

import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.cast
import kotlin.reflect.jvm.javaGetter

// Although we need to use reflection for this to work, we do that OUTSIDE of the verify function when we are creating
// the state so that we don't need to use reflection within the smart contract
class ArbitarilyOpinionatedContract: Contract {

    override fun verify(tx: LedgerTransaction) {
        val obj = tx.references[0]
        val arbitraryOpinion = tx.referenceInputRefsOfType<ArbitrarilyOpinionatedContractState<*, *>>().first()
        val expectedTypeForObject = arbitraryOpinion.state.data.castToType
        val objAsExpectedType = expectedTypeForObject.cast(obj)
        val operand = arbitraryOpinion.state.data.operandAccess.call(obj)


    }
}

data class ArbitrarilyOpinionatedContractState<T: StateAndRef<ContractState>, R: List<*>>(
        val castToType: KClass<T>,
        val operandAccess: KCallable<R>,
        override val participants: List<AbstractParty> = listOf()): ContractState