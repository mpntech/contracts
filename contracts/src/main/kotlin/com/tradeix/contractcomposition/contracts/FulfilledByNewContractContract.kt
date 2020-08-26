package com.tradeix.contractcomposition.contracts

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class FulfilledByNewContractContract: Contract {
    override fun verify(tx: LedgerTransaction) {}
}