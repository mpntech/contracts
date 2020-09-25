package com.tradeix.contractcomposition.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.tradeix.contractcomposition.contracts.commands.CreateBasicContractsCommand
import com.tradeix.contractcomposition.contracts.common.StateRefOrRef
import com.tradeix.contractcomposition.contracts.states.*
import net.corda.core.contracts.ContractState
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
@InitiatingFlow
// This flow is just for test purposes. All main contract subcontracts are just dummy composites with no subcontracts
class IssueBasicStandardContract(
        val contractToIssue: ContractState,
        notary: Party
): FlowLogic<SignedTransaction>() {
    private val transactionBuilder: TransactionBuilder = TransactionBuilder(notary = notary)
    @Suspendable
    override fun call(): SignedTransaction {
        val me = serviceHub.myInfo.legalIdentities.first()
        // add basic standard contract
        transactionBuilder.addOutputState(contractToIssue)
        // add some dummy composite contracts which the above basic contract might be (optionally) pointing to
        repeat(4) {
            transactionBuilder.addOutputState(CompositeContractState(contracts = listOf(), participants = listOf(me)))
        }

        transactionBuilder.addCommand(CreateBasicContractsCommand(), listOf(me.owningKey))
        val signedTx = serviceHub.signInitialTransaction(transactionBuilder, me.owningKey)
        // where do I verify it before recording?
        serviceHub.recordTransactions(signedTx)
        return signedTx
    }
}