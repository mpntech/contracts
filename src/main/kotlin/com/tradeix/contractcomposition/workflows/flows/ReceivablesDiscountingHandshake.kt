package com.tradeix.contractcomposition.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import java.time.LocalDateTime
import java.util.*

@InitiatingFlow
@StartableByRPC
class ReceivablesDiscountingContractInitiator(
        val counterParty: Party,
        val purchaseAmount: Amount<Currency>,
        val expirationDateTime: LocalDateTime,
        val tradePaymentDueDateTime: LocalDateTime,
        val repaymentSchedule: List<Map<Amount<Currency>, LocalDateTime>>,
        val funder: AbstractParty,
        val supplier: AbstractParty
): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val counterpartySession = initiateFlow(counterParty)
        return subFlow(
                ExampleFlow(
                        sessions = listOf(counterpartySession),
                        purchaseAmount = purchaseAmount,
                        expirationDateTime = expirationDateTime,
                        tradePaymentDueDateTime = tradePaymentDueDateTime,
                        repaymentSchedule = repaymentSchedule,
                        funder = funder,
                        supplier = supplier
                ))
    }
}

// Can fix this later to call into a custom ReceiveFundingResponseFlow (rather than into ReceiveFinalityFlow directly)
// which takes the new states and maps them into another table where we can deal with the response as a single object
@InitiatedBy(ReceivablesDiscountingContractInitiator::class)
class ReceivablesDiscountingContractHandler(val otherSession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        return subFlow(ReceiveFinalityFlow(otherSession))
    }

}