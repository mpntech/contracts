package com.tradeix.contractcomposition.contracts.states

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import java.util.*

/**
 * WARNING: This class will be removed from this library. It is simply being used in the ExampleFlow for now so we need
 * it in here. But it's actually crap so should be in client app if it's needed at all
 *
 * It's simply a signed claim that some party made a payment of some amount to some other party off-chain. Will die
 * as soon as we have cash-like value on-chain
 */

data class DummyPaymentState(
        val payor: AbstractParty,
        val payee: AbstractParty,
        val amountPaid: Amount<Currency>,
        // arbitrary additional data about the off-chain payment (bank account details, invoice payment was for, etc.)
        val data: String,
        override val participants: List<AbstractParty> = listOf(payor, payee)): ContractState