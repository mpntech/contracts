package com.tradeix.contractcomposition.workflows.receivablesdiscounting

import com.tradeix.contractcomposition.contracts.common.StateRefOrRef
import com.tradeix.contractcomposition.contracts.states.*
import com.tradeix.contractcomposition.workflows.flows.FulfillStandardContractFlow
import com.tradeix.contractcomposition.workflows.flows.IssueBasicStandardContract
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException
import java.util.concurrent.ExecutionException
import kotlin.test.assertFailsWith

class FulfillStandardContractFlowTests {

    companion object {
        private lateinit var network: MockNetwork
        private lateinit var supplier: StartedMockNode
        private lateinit var funder: StartedMockNode
        val supplierParty = CordaX500Name("MiniCorp", "London", "GB")
        val funderParty = CordaX500Name("MegaCorp", "London", "GB")
        @BeforeAll
        @JvmStatic
        fun init() {
            network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                    TestCordapp.findCordapp("com.tradeix.contractcomposition.contracts"),
                    TestCordapp.findCordapp("com.tradeix.contractcomposition.workflows")
            ),
                    networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
            ))

            supplier = network.createPartyNode(supplierParty)
            funder = network.createPartyNode(funderParty)
            network.runNetwork()
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            network.stopNodes()
        }
    }

    @Test
    fun `fulfill basic composite contract`() {
        // Create basic composite contract
        val futureCompositeIssuanceTx = funder.startFlow(IssueBasicStandardContract(
                contractToIssue = CompositeContractState(
                        contracts = listOf(StateRefOrRef(ref = 1), StateRefOrRef(ref = 2)),
                        participants = listOf(funder.info.legalIdentities.first())
                ),
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        val compositeIssuanceTx = futureCompositeIssuanceTx.toCompletableFuture().get()
        // contractToIssue must always be added first in standard issuance flow or this will fail - index is assumed to be 0
        val compositeStateAndRef = StateAndRef(
                state = compositeIssuanceTx.coreTransaction.outputs.first(),
                ref = StateRef(compositeIssuanceTx.id, 0)
        )
        val futureCompositeFulfillmentTx = funder.startFlow(FulfillStandardContractFlow(
                contractToFulfill = compositeStateAndRef,
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        val compositeFulfillmentTx = futureCompositeFulfillmentTx.toCompletableFuture().get()!!
        val fulfilledContractsStates = compositeFulfillmentTx.coreTransaction.outputsOfType<FulfilledContractsState>()
                .flatMap { it.fulfilledContracts }

        // One of the fulfilled contracts state outputs claims that the contract we needed to fulfill was fulfilled.
        // We trust that if the flow succeeds, it means the returned transaction was valid and committed to vault.
        // So, we can just check that returned tx directly rather than go to vault and see what's occurred there.
        assert(fulfilledContractsStates.contains(compositeStateAndRef.ref))

    }

    @Test
    fun `fulfill basic multipath contract`() {
        // Create basic composite contract
        val futureMultipathIssuanceTx = funder.startFlow(IssueBasicStandardContract(
                contractToIssue = MultipathContractState(
                        contracts = listOf(StateRefOrRef(ref = 1), StateRefOrRef(ref = 2)),
                        participants = listOf(funder.info.legalIdentities.first())
                ),
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        val multipathIssuanceTx = futureMultipathIssuanceTx.toCompletableFuture().get()
        // contractToIssue must always be added first in standard issuance flow or this will fail - index is assumed to be 0
        val multipathStateAndRef = StateAndRef(
                state = multipathIssuanceTx.coreTransaction.outputs.first(),
                ref = StateRef(multipathIssuanceTx.id, 0)
        )
        // Prepare to instruct fulfillment flow which path to use to fulfill our multi-path
        val pathToFulfill = mapOf(pair = Pair(multipathStateAndRef.ref, StateRefOrRef(ref = 1)))
        val futureMultipathFulfillmentTx = funder.startFlow(FulfillStandardContractFlow(
                contractToFulfill = multipathStateAndRef,
                pathsToFulfill = pathToFulfill,
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        val multipathFulfillmentTx = futureMultipathFulfillmentTx.toCompletableFuture().get()!!
        val fulfilledContractsStates = multipathFulfillmentTx.coreTransaction.outputsOfType<FulfilledContractsState>()
                .flatMap { it.fulfilledContracts }

        // One of the fulfilled contracts state outputs claims that the contract we needed to fulfill was fulfilled.
        // We trust that if the flow succeeds, it means the returned transaction was valid and committed to vault.
        // So, we can just check that returned tx directly rather than go to vault and see what's occurred there.
        assert(fulfilledContractsStates.contains(multipathStateAndRef.ref))
    }

    @Test
    fun `fulfill basic detached contract`() {
        // Create basic composite contract
        val futureDetachedIssuanceTx = funder.startFlow(IssueBasicStandardContract(
                contractToIssue = DetachedContractState(
                        requiredFulfilledId = StateRefOrRef(ref = 1),
                        participants = listOf(funder.info.legalIdentities.first())
                ),
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        val detachedIssuanceTx = futureDetachedIssuanceTx.toCompletableFuture().get()
        // contractToIssue must always be added first in standard issuance flow or this will fail - index is assumed to be 0
        val detachedStateAndRef = StateAndRef(
                state = detachedIssuanceTx.coreTransaction.outputs.first(),
                ref = StateRef(detachedIssuanceTx.id, 0)
        )

        // Before we can fulfill the detached contract, we need to fulfill the contract it points to in a prior tx.
        // We do that now by passing that subcontract into the FulfillStandardContractFlow
        val referencedContractStateAndRef = StateAndRef(
                state = detachedIssuanceTx.coreTransaction.outputs[1],
                ref = StateRef(txhash = detachedIssuanceTx.id, index = 1)
        )
        val futureReferencedFulfillmentTx = funder.startFlow(FulfillStandardContractFlow(
                contractToFulfill = referencedContractStateAndRef,
                notary = network.defaultNotaryNode.info.legalIdentities.first()))

        network.runNetwork()

        val referencedFulfillmentTx = futureReferencedFulfillmentTx.toCompletableFuture().get()
        // StateAndRef of "ticket" (FulfilledContractsState) which proves the subcontract was met.
        // We'll use this in the next transaction to fulfill our original detached contract state
        val ticketStateAndRef = referencedFulfillmentTx.coreTransaction.outRefsOfType<FulfilledContractsState>().first()

        // Prepare to instruct fulfillment flow which FulfilledContractsState to add to the ref inputs list as proof
        // that the detached contract has already been fulfilled
        val ticketToUse = mapOf(pair = Pair(detachedStateAndRef.ref, ticketStateAndRef))
        val futureDetachedFulfillmentTx = funder.startFlow(FulfillStandardContractFlow(
                contractToFulfill = detachedStateAndRef,
                ticketsToUse = ticketToUse,
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        val detachedFulfillmentTx = futureDetachedFulfillmentTx.toCompletableFuture().get()!!
        val fulfilledContractsStates = detachedFulfillmentTx.coreTransaction.outputsOfType<FulfilledContractsState>()
                .flatMap { it.fulfilledContracts }

        // One of the fulfilled contracts state outputs claims that the contract we needed to fulfill was fulfilled.
        // We trust that if the flow succeeds, it means the returned transaction was valid and committed to vault.
        // So, we can just check that returned tx directly rather than go to vault and see what's occurred there.
        assert(fulfilledContractsStates.contains(detachedStateAndRef.ref))
    }

    @Test
    fun `fulfill basic double-spend prevention contract`() {
        val futureDSPIssuanceTx = funder.startFlow(IssueBasicStandardContract(
                contractToIssue = DoubleSpendPreventionState(
                        statesToNotarize = listOf(StateRefOrRef(ref = 1)),
                        participants = listOf(funder.info.legalIdentities.first())
                ),
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        val dspIssuanceTx = futureDSPIssuanceTx.toCompletableFuture().get()
        // contractToIssue must always be added first in standard issuance flow or this will fail - index is assumed to be 0
        val dspStateAndRef = StateAndRef(
                state = dspIssuanceTx.coreTransaction.outputs.first(),
                ref = StateRef(dspIssuanceTx.id, 0)
        )

        val futureDSPFulfillmentTx = funder.startFlow(FulfillStandardContractFlow(
                contractToFulfill = dspStateAndRef,
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        val dspFulfillmentTx = futureDSPFulfillmentTx.toCompletableFuture().get()!!
        val fulfilledContractsStates = dspFulfillmentTx.coreTransaction.outputsOfType<FulfilledContractsState>()
                .flatMap { it.fulfilledContracts }

        // One of the fulfilled contracts state outputs claims that the contract we needed to fulfill was fulfilled.
        // We trust that if the flow succeeds, it means the returned transaction was valid and committed to vault.
        // So, we can just check that returned tx directly rather than go to vault and see what's occurred there.
        assert(fulfilledContractsStates.contains(dspStateAndRef.ref))
    }

    @Test
    fun `fulfill basic key-control contract`() {
        val futureKeyControlIssuanceTx = funder.startFlow(IssueBasicStandardContract(
                contractToIssue = KeyControlState(
                        key = AnonymousParty(funder.services.keyManagementService.freshKey()),
                        participants = listOf(funder.info.legalIdentities.first())
                ),
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        val keyControlIssuanceTx = futureKeyControlIssuanceTx.toCompletableFuture().get()
        // contractToIssue must always be added first in standard issuance flow or this will fail - index is assumed to be 0
        val keyControlStateAndRef = StateAndRef(
                state = keyControlIssuanceTx.coreTransaction.outputs.first(),
                ref = StateRef(keyControlIssuanceTx.id, 0)
        )
        val futureKeyControlFulfillmentTx = funder.startFlow(FulfillStandardContractFlow(
                contractToFulfill = keyControlStateAndRef,
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        val keyControlFulfillmentTx = futureKeyControlFulfillmentTx.toCompletableFuture().get()!!
        val fulfilledContractsStates = keyControlFulfillmentTx.coreTransaction.outputsOfType<FulfilledContractsState>()
                .flatMap { it.fulfilledContracts }

        // One of the fulfilled contracts state outputs claims that the contract we needed to fulfill was fulfilled.
        // We trust that if the flow succeeds, it means the returned transaction was valid and committed to vault.
        // So, we can just check that returned tx directly rather than go to vault and see what's occurred there.
        assert(fulfilledContractsStates.contains(keyControlStateAndRef.ref))
    }

    @Test
    fun `fulfill basic fulfilled-by-new-contract contract`() {
        // More likely to be something like a dummy payment state, but just a key control here for simplicity
        val requiredNewContract = KeyControlState(
                key = funder.info.legalIdentities.first(),
                participants = listOf(funder.info.legalIdentities.first()))

        val futureByNewContractIssuanceTx = funder.startFlow(IssueBasicStandardContract(
                contractToIssue = FulfilledByNewContract(
                        newContract = requiredNewContract,
                        participants = listOf(funder.info.legalIdentities.first())
                ),
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        val byNewContractIssuanceTx = futureByNewContractIssuanceTx.toCompletableFuture().get()
        // contractToIssue must always be added first in standard issuance flow or this will fail - index is assumed to be 0
        val byNewContractStateAndRef = StateAndRef(
                state = byNewContractIssuanceTx.coreTransaction.outputs.first(),
                ref = StateRef(byNewContractIssuanceTx.id, 0)
        )
        val newContractToCreate = mapOf(pair = Pair(byNewContractStateAndRef.ref, requiredNewContract))
        val futureByNewContractFulfillmentTx = funder.startFlow(FulfillStandardContractFlow(
                contractToFulfill = byNewContractStateAndRef,
                newContractsToCreate = newContractToCreate,
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        val byNewContractFulfillmentTx = futureByNewContractFulfillmentTx.toCompletableFuture().get()!!
        val fulfilledContractsStates = byNewContractFulfillmentTx.coreTransaction.outputsOfType<FulfilledContractsState>()
                .flatMap { it.fulfilledContracts }

        // One of the fulfilled contracts state outputs claims that the contract we needed to fulfill was fulfilled.
        // We trust that if the flow succeeds, it means the returned transaction was valid and committed to vault.
        // So, we can just check that returned tx directly rather than go to vault and see what's occurred there.
        assert(fulfilledContractsStates.contains(byNewContractStateAndRef.ref))
    }

    @Test
    fun `attempt to fulfill multipath without specifying path`() {
        // Create basic composite contract
        val futureMultipathIssuanceTx = funder.startFlow(IssueBasicStandardContract(
                contractToIssue = MultipathContractState(
                        contracts = listOf(StateRefOrRef(ref = 1), StateRefOrRef(ref = 2)),
                        participants = listOf(funder.info.legalIdentities.first())
                ),
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        val multipathIssuanceTx = futureMultipathIssuanceTx.toCompletableFuture().get()
        // contractToIssue must always be added first in standard issuance flow or this will fail - index is assumed to be 0
        val multipathStateAndRef = StateAndRef(
                state = multipathIssuanceTx.coreTransaction.outputs.first(),
                ref = StateRef(multipathIssuanceTx.id, 0)
        )
        // Neglect to provide a chosen path for the multipath we want to fulfill
        val pathToFulfill = emptyMap<StateRef, StateRefOrRef>()
        val futureMultipathFulfillmentTx = funder.startFlow(FulfillStandardContractFlow(
                contractToFulfill = multipathStateAndRef,
                pathsToFulfill = pathToFulfill,
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        assertFailsWith<ExecutionException> { futureMultipathFulfillmentTx.toCompletableFuture().get()!! }
    }

    @Test
    fun `attempt to fulfill by-new-contract without specifying output to create`() {
        val requiredNewContract = KeyControlState(
                key = funder.info.legalIdentities.first(),
                participants = listOf(funder.info.legalIdentities.first()))

        val futureByNewContractIssuanceTx = funder.startFlow(IssueBasicStandardContract(
                contractToIssue = FulfilledByNewContract(
                        newContract = requiredNewContract,
                        participants = listOf(funder.info.legalIdentities.first())
                ),
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        val byNewContractIssuanceTx = futureByNewContractIssuanceTx.toCompletableFuture().get()
        // contractToIssue must always be added first in standard issuance flow or this will fail - index is assumed to be 0
        val byNewContractStateAndRef = StateAndRef(
                state = byNewContractIssuanceTx.coreTransaction.outputs.first(),
                ref = StateRef(byNewContractIssuanceTx.id, 0)
        )
        // Not providing output for ByNewContract we are attempting to fulfill. Instead of guessing, the flow should
        // refuse to build the tx
        val newContractToCreate = emptyMap<StateRef, ContractState>()
        val futureByNewContractFulfillmentTx = funder.startFlow(FulfillStandardContractFlow(
                contractToFulfill = byNewContractStateAndRef,
                newContractsToCreate = newContractToCreate,
                notary = network.defaultNotaryNode.info.legalIdentities.first()
        ))

        network.runNetwork()

        assertFailsWith<ExecutionException> { futureByNewContractFulfillmentTx.toCompletableFuture().get()!! }
    }
}