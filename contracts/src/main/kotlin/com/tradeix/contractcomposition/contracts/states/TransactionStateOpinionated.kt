package com.tradeix.contractcomposition.contracts.states

import com.tradeix.contractcomposition.contracts.common.DataOpinionatedContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

data class TransactionStateOpinionated(
        val equalsValue: Any,
        override val operator: (Any) -> Boolean = { it == equalsValue },
        override val participants: List<AbstractParty>
): ContractState, DataOpinionatedContract {
    fun doIt (): Boolean{
        val x = 6
        return operator{equalsValue}
    }
}

val x = net.corda.core.contracts.ContractState::class.java