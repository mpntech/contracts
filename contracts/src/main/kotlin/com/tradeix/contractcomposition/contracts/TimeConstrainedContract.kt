package com.tradeix.contractcomposition.contracts

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class TimeConstrainedContract: Contract {
    override fun verify(tx: LedgerTransaction) {
        TODO("not implemented")
    }
}