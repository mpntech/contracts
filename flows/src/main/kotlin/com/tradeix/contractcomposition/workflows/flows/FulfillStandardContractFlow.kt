package com.tradeix.contractcomposition.workflows.flows


import co.paralleluniverse.fibers.Suspendable
import com.tradeix.contractcomposition.contracts.CompositeContract
import com.tradeix.contractcomposition.contracts.commands.FulfillRefInputsCommand
import com.tradeix.contractcomposition.contracts.common.StateRefOrRef
import com.tradeix.contractcomposition.contracts.states.*
import com.tradeix.contractcomposition.workflows.utilities.*
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.internal.signWithCert
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.io.IOException
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.security.PublicKey
import java.time.Duration
import java.time.Instant

/**
 * This is another example flow which allows fulfillment of any standard contract.
 *
 * It will need to be extended in future to allow more flexibility for clients to instruct this flow on how they want
 * to fulfill specific sub-contracts which fall under the main one. This will become especially important as we go
 * from Multipath contracts (where the client simply needs to specify the path we should take to build the transaction)
 * to Variablepath contracts (where valid paths are calculated on the fly rather than pre-built into the contract state).
 *
 * For now, however, clients can only instruct this flow about A.) which path to take when it encounters a Multipath
 * and B.) what new contract to create when it encounters a FulfilledByNewContract
 *
 * The class is open so that it can be extended to deal with more complex types of fulfillment requirements in future
 * (e.g. a FulfilledByNewContract which needs a FulfilledContractsState as an output and therefore needs to query the
 * vault to find the best way to create the required output)
 */

//Probably shouldn't be initiating but is for now so it's easier to test
@InitiatingFlow
@StartableByRPC
open class FulfillStandardContractFlow(
        // State could be resolved in this flow but for now we'll say it's resolved outside and passed in as StateAndRef
        val contractToFulfill: StateAndRef<ContractState>,
        val notary: Party,
        // Only populated if we want to send this tx to parties in addition those on participants list
        val sessions: List<FlowSession> = listOf(),
        val pathsToFulfill: Map<StateRef, StateRefOrRef> = emptyMap(),
        val ticketsToUse: Map<StateRef, StateAndRef<FulfilledContractsState>> = emptyMap(),
        val newContractsToCreate: Map<StateRef, ContractState> = emptyMap()
): FlowLogic<SignedTransaction>() {
    private val requiredSignatures = mutableListOf<PublicKey>()
    private val transactionBuilder = TransactionBuilder(notary = notary)
    @Suspendable
    override fun call(): SignedTransaction {
        // First, we simply add the state which client has asked us to fulfill to ref inputs list
        transactionBuilder.addReferenceState(ReferencedStateAndRef(contractToFulfill))
        // Then we create a FulfilledContractsState as proof that the above was fulfilled in case client needs it later
        // May want to give them the option to tell us in the constructor if they would like proof of any subcontract
        // There definitely may be cases when they do. For example, funder takes back their collateral with a higher
        // contract but wants to retain proof of trade payment so they can use that later if supplier defaults
        transactionBuilder.addOutputState(
                state = FulfilledContractsState(
                        fulfilledContracts = listOf(contractToFulfill.ref),
                        // Just needs to exist, its fulfillment is irrelevant. So will just point to itself for required contracts (essentially null)
                        contracts = listOf(StateRefOrRef(ref = 0)),
                        participants = contractToFulfill.state.data.participants))

        // Our fulfilled ref inputs list will be the index of the ref input we just added and all the indices of the
        // other contracts (if any) we need to also fulfill in order to fulfill the top-tier contract
        val fulfilledRefInputs = listOf(
                listOf(transactionBuilder.referenceStates().size - 1),
                fulfillContractByType(contractToFulfill)).flatten()
        // add command
        if (requiredSignatures.isEmpty()) {
            requiredSignatures.add(serviceHub.myInfo.legalIdentities.first().owningKey)
        }
        transactionBuilder.addCommand(
                data = CompositeContract.FulfillCompositeContracts(fulfilledRefInputs),
                keys = requiredSignatures)

        val participants = transactionBuilder.outputStates().flatMap { it.data.participants }
        val wellKnownParticipants = groupAbstractPartyByWellKnownParty(serviceHub, participants).keys - serviceHub.myInfo.legalIdentities
        val participantSessions = wellKnownParticipants.map { initiateFlow(it) }

        // fix to sign for each key not just the first
        // Note that this flow only supports unilateral actions meaning if there are signatures which are required from
        // other parties on the network, this will fail.
        val signedTx = serviceHub.signInitialTransaction(transactionBuilder, requiredSignatures.first())
        return subFlow(FinalityFlow(signedTx, participantSessions + sessions))
    }

    //Not sure if these need to be suspendable but setting them as such because was getting a weird error in tests...
    @Suspendable
    private fun fulfillContractByType(contractToFulfill: StateAndRef<ContractState>): List<Int> {
        return when (contractToFulfill.state.data) {
            is CompositeContractState -> listOf(fulfillCompositeContract(contractToFulfill)).flatten()
            is MultipathContractState -> listOf(fulfillMultipathContract(contractToFulfill)).flatten()
            is DetachedContractState -> listOf(fulfillDetachedContract(contractToFulfill)).flatten()
            is DoubleSpendPreventionState -> listOf(fulfillDSPContract(contractToFulfill)).flatten()
            is TimeConstrainedState -> listOf(fulfillTimeConstrainedContract(contractToFulfill)).flatten()
            is KeyControlState -> listOf(fulfillKeyControlContract(contractToFulfill)).flatten()
            is FulfilledByNewContract -> listOf(fulfillByNewContract(contractToFulfill)).flatten()
            // The transaction will normally fail if this block is ever reached and the class hasn't been extended.
            // This would mean the contracts or one of its sub-contracts is non-standard and therefore we're not
            // going to do anything to attempt to fulfill it.
            // However, we allow it to be extended so that we can create flows which need to fulfill non-standard
            // contracts in the overall chain, but which want to fulfill the standard contracts it sees in the chain
            // in the same way that the flow here handles them
            else -> listOf(fulfillNonStandardContract(contractToFulfill)).flatten()
        }
    }

    @Suspendable
    private fun fulfillCompositeContract(compositeContractToFulfill: StateAndRef<ContractState>): List<Int> {
        var addedRefIndices = listOf<Int>()
        for (subContract in ((compositeContractToFulfill.state.data as CompositeContractState).contracts)) {
            val subContractStateAndRef = serviceHub.toStateAndRef<ContractState>(
                    subContract.stateRef
                            ?: StateRef(txhash = compositeContractToFulfill.ref.txhash, index = subContract.ref!!))
            transactionBuilder.addReferenceState(ReferencedStateAndRef(subContractStateAndRef))
            addedRefIndices =
                    listOf(addedRefIndices,
                            listOf(transactionBuilder.referenceStates().size - 1),
                            fulfillContractByType(subContractStateAndRef)).flatten()
        }
        return addedRefIndices
    }

    @Suspendable
    private fun fulfillMultipathContract(multipathContractToFulfill: StateAndRef<ContractState>): List<Int> {
        val fulfilledPath = pathsToFulfill[multipathContractToFulfill.ref]
        val fulfilledPathStateAndRef =
                serviceHub.toStateAndRef<ContractState>(
                        fulfilledPath?.stateRef ?: StateRef(
                                txhash = multipathContractToFulfill.ref.txhash,
                                index = fulfilledPath?.ref!!))

        transactionBuilder.addReferenceState(ReferencedStateAndRef(fulfilledPathStateAndRef))

        return listOf(
                listOf(transactionBuilder.referenceStates().size - 1),
                fulfillContractByType(fulfilledPathStateAndRef)).flatten()
    }

    @Suspendable
    private fun fulfillDetachedContract(detachedContractToFulfill: StateAndRef<ContractState>): List<Int> {
        val ticketToAdd = ticketsToUse[detachedContractToFulfill.ref]
        transactionBuilder.addReferenceState(ReferencedStateAndRef(ticketToAdd!!))
        return if ((detachedContractToFulfill.state.data as DetachedContractState).detachedProofFulfilled) {
            listOf(listOf(transactionBuilder.referenceStates().size - 1), fulfillContractByType(ticketToAdd)).flatten()
        }
        // If we're not required to fulfill the proof of previous fulfillment (ticket), it's added as a normal ref input
        else listOf()
    }

    @Suspendable
    private fun fulfillDSPContract(dspContractToFulfill: StateAndRef<ContractState>): List<Int> {
        val dspContractState = dspContractToFulfill.state.data as DoubleSpendPreventionState
        val requiredInputStateAndRef =
                serviceHub.toStateAndRef<ContractState>(
                        dspContractState.statesToNotarize.first().stateRef?: StateRef(
                                txhash = dspContractToFulfill.ref.txhash,
                                index = dspContractState.statesToNotarize.first().ref!!))

        transactionBuilder.addInputState(requiredInputStateAndRef)

        return fulfillContractByType(requiredInputStateAndRef)
    }

    // Is it appropriate to put validation in here? That is, if we know the time is not going to meet the requirement
    // in the TimeConstrainedState should we throw an exception or let that happen when it gets to verification?
    // For now, I have not included it. I just set the time window and let the contract code throw the error if needed
    @Suspendable
    private fun fulfillTimeConstrainedContract(timeConstrainedContractToFulfill: StateAndRef<ContractState>): List<Int> {
        // Because we don't have access to the window property on the builder, we can't check whether one already exists
        // So instead, we'll just add one regardless of whether another is already there
        // if (transactionBuilder.window != null) return listOf()
        // I have no idea what we should set the time tolerance to...need to investigate what the default should be
        transactionBuilder.setTimeWindow(TimeWindow.withTolerance(Instant.now(), Duration.ofSeconds(120)))
        return listOf()
    }

    // Add the key on the KeyControl state to the overall list of required signatures which we will use at the end
    // to build our fulfill command and sign the transaction n number of times.
    // No additional fulfilled ref inputs added so return an empty list
    @Suspendable
    private fun fulfillKeyControlContract(keyControlStateToFulfill: StateAndRef<ContractState>): List<Int> {
        requiredSignatures.add((keyControlStateToFulfill.state.data as KeyControlState).key.owningKey)
        return listOf()
    }

    // Add the required output and then call into the open facilitateContractCreation function to do anything which the
    // output's contract is going to require in order for it to allow the output to be validly created.
    // For example, if we were required to output a certain FulfilledContractsState (as will be extremely common) then
    // we would need that function to go ahead and ensure that all contracts in the list of fulfilled contracts on the
    // FulfilledContractsState were also being fulfilled.
    // We may find that we can eventually handle this bit within this class and can close the function, but it needs to
    // be open for now as there is logic we don't need to implement today which will be absolutely critical later
    @Suspendable
    private fun fulfillByNewContract(byNewContractToFulfill: StateAndRef<ContractState>): List<Int> {
        // Makes our lives easier to deal with ContractStates for now, but outputs should really be specified as txStates
        val requiredOutput = newContractsToCreate[byNewContractToFulfill.ref]
        if (requiredOutput == null) throw IllegalArgumentException()
        else {
            transactionBuilder.addOutputState(requiredOutput)
            return listOf(facilitateContractCreation(requiredOutput)).flatten()
        }
    }
    @Suspendable
    open fun fulfillNonStandardContract(nonStandardContractToFulfill: StateAndRef<ContractState>): List<Int> {
        return listOf()
    }
    @Suspendable
    open fun facilitateContractCreation(newContract: ContractState?): List<Int> {
        return listOf()
    }
}

// Can put logic in here to slot fulfilled contracts into a custom table or do whatever else we need to do when we hear
// about certain contracts being fulfilled. For example, funder needs to go and make a payment if they here that supplier
// has fulfilled the acceptance contract, in the case of receivables discounting
@InitiatedBy(FulfillStandardContractFlow::class)
open class ReceiveFulfilledContractTx(private val otherSideSession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        return subFlow(ReceiveFinalityFlow(otherSideSession = otherSideSession))
    }
}