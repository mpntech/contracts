package com.tradeix.contractcomposition.workflows.utilities

import co.paralleluniverse.fibers.Suspendable
import com.tradeix.contractcomposition.contracts.common.StateRefOrRef
import com.tradeix.contractcomposition.contracts.states.*
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder

@Suspendable
// Helper function to add a collection of output states which together manage a moveable "right" or "token"
fun addNewEvolvableRight(tx: TransactionBuilder, originalControl: ContractState, participants: List<AbstractParty>): TransactionBuilder {

    // In order for the new outputs to reference each other correctly, we need to know where in the list they'll be added
    val currentLastOutputIndex = tx.outputStates().lastIndex

    /**
     * We return the transaction builder with 7 new outputs states tacked onto the builder
     * The first is what we can call a "ContractManager". It's a Multipath contract which can be fulfilled in one of two ways
     * The second TODO: finish this description as it's important
     *
     * We always add the Multipath first as this will likely need to be referenced by other outputs added to the tx so the
     * index should be easily known
     */
    return tx
            .addOutputState(MultipathContractState(
                    contracts = listOf(StateRefOrRef(ref = currentLastOutputIndex + 2), StateRefOrRef(ref = currentLastOutputIndex + 3)),
                    participants = participants))
            .addOutputState(CompositeContractState(
                    contracts = listOf(StateRefOrRef(ref = currentLastOutputIndex + 4), StateRefOrRef(ref = currentLastOutputIndex + 5)),
                    participants = participants))
            .addOutputState(CompositeContractState(
                    contracts = listOf(StateRefOrRef(ref = currentLastOutputIndex + 6), StateRefOrRef(ref = currentLastOutputIndex + 7)),
                    participants = participants))
            .addOutputState(originalControl)
            .addOutputState(DoubleSpendPreventionState(
                    statesToNotarize = listOf(StateRefOrRef(ref = currentLastOutputIndex + 4)),
                    participants = participants))
            .addOutputState(DetachedContractState(
                    requiredFulfilledId = StateRefOrRef(ref = currentLastOutputIndex + 1),
                    participants = participants))
            .addOutputState(DoubleSpendPreventionState(
                    statesToNotarize = listOf(StateRefOrRef(ref = currentLastOutputIndex + 6)),
                    participants = participants))
}

@Suspendable
fun addNewRightWithAmount(tx: TransactionBuilder, originalControl: ContractState) {

}

@Suspendable
fun addNewContractManager(tx: TransactionBuilder) {}

@Suspendable
// Adds the required inputs & ref inputs to the tx builder in order to validly execute this contract with chosen paths
// and then returns a list of ref input indices which should be added to some FulfilledRefInputsCommand as last step
fun fulfillStandardContract(
        contractStateAndRef: StateAndRef<ContractState>,
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        fulfilledPaths: Map<StateRef, StateRefOrRef>): List<Int> {

    when (contractStateAndRef.state.data) {
        is CompositeContractState ->
            return listOf(fulfillCompositeContract(
                    contractStateAndRef as StateAndRef<CompositeContractState>, transactionBuilder, serviceHub, fulfilledPaths)).flatten()
        is MultipathContractState ->
            return listOf(fulfillMultipathContract(
                    contractStateAndRef as StateAndRef<MultipathContractState>, transactionBuilder, serviceHub, fulfilledPaths)).flatten()
        is DetachedContractState ->
            return listOf(fulfillDetachedContract(
                    contractStateAndRef as StateAndRef<DetachedContractState>, transactionBuilder, serviceHub, fulfilledPaths)).flatten()
        is DoubleSpendPreventionState ->
            return listOf(fulfillDSPContract(
                    contractStateAndRef as StateAndRef<DoubleSpendPreventionState>, transactionBuilder, serviceHub, fulfilledPaths)).flatten()
        is TimeConstrainedState ->
            return listOf(fulfillTimeConstrainedContract(
                    contractStateAndRef as StateAndRef<TimeConstrainedState>, transactionBuilder, serviceHub, fulfilledPaths)).flatten()
        is KeyControlState ->
            return listOf(fulfillKeyControlContract(
                    contractStateAndRef as StateAndRef<KeyControlState>, transactionBuilder, serviceHub, fulfilledPaths)).flatten()
        is FulfilledByNewContract ->
            return listOf(fulfillByNewContract(
                    contractStateAndRef as StateAndRef<FulfilledByNewContract>, transactionBuilder, serviceHub, fulfilledPaths)).flatten()
        else ->
            return listOf()
    }
}


private fun fulfillCompositeContract(
        contractStateAndRef: StateAndRef<CompositeContractState>,
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        fulfilledPaths: Map<StateRef, StateRefOrRef>): List<Int> {

    var addedRefIndices = listOf<Int>()
    for (subContract in contractStateAndRef.state.data.contracts) {
        val subContractStateRef = subContract.stateRef ?: StateRef(txhash = contractStateAndRef.ref.txhash, index = subContract.ref!!)
        val subContractStateAndRef = serviceHub.toStateAndRef<ContractState>(subContractStateRef)
        transactionBuilder.addReferenceState(ReferencedStateAndRef(subContractStateAndRef))
        addedRefIndices =
                listOf(addedRefIndices,
                        listOf(transactionBuilder.outputStates().size - 1),
                        fulfillStandardContract(subContractStateAndRef, transactionBuilder, serviceHub, fulfilledPaths)).flatten()
    }
    return addedRefIndices
}

fun fulfillMultipathContract(
    contractStateAndRef: StateAndRef<MultipathContractState>,
    transactionBuilder: TransactionBuilder,
    serviceHub: ServiceHub,
    fulfilledPaths: Map<StateRef, StateRefOrRef>): List<Int> {

    val fulfilledPath = fulfilledPaths[contractStateAndRef.ref]
    val fulfilledPathStateAndRef =
            serviceHub.toStateAndRef<ContractState>(
                    fulfilledPath?.stateRef?: StateRef(txhash = contractStateAndRef.ref.txhash, index = fulfilledPath?.ref!!))

    transactionBuilder.addReferenceState(ReferencedStateAndRef(fulfilledPathStateAndRef))

    return listOf(
            listOf(transactionBuilder.outputStates().size - 1),
            fulfillStandardContract(
                    fulfilledPathStateAndRef, transactionBuilder, serviceHub, fulfilledPaths)).flatten()
}

fun fulfillDetachedContract(
    contractStateAndRef: StateAndRef<DetachedContractState>,
    transactionBuilder: TransactionBuilder,
    serviceHub: ServiceHub,
    fulfilledPaths: Map<StateRef, StateRefOrRef>): List<Int> {

    // TODO: Fix FulfilledContractsState so that we can query by its fulfilled StateRefs and get one that fulfills this DetachedContract
    // We can't complete this function until that has been done

    return listOf(1)
}

fun fulfillDSPContract(
    contractStateAndRef: StateAndRef<DoubleSpendPreventionState>,
    transactionBuilder: TransactionBuilder,
    serviceHub: ServiceHub,
    fulfilledPaths: Map<StateRef, StateRefOrRef>): List<Int> {

    // we'll just assume the states to notarize only ever contains one state for now...
    val requiredInputStateAndRef =
            serviceHub.toStateAndRef<ContractState>(
            contractStateAndRef.state.data.statesToNotarize.first().stateRef?: StateRef(
                    txhash = contractStateAndRef.ref.txhash,
                    index = contractStateAndRef.state.data.statesToNotarize.first().ref!!))

    transactionBuilder.addInputState(requiredInputStateAndRef)

    return listOf(
            listOf(transactionBuilder.inputStates().size - 1),
            fulfillStandardContract(
                    requiredInputStateAndRef, transactionBuilder, serviceHub, fulfilledPaths)).flatten()
    }

// Note that this function doesn't care whether the notary is likely to sign this
fun fulfillTimeConstrainedContract(
    contractStateAndRef: StateAndRef<TimeConstrainedState>,
    transactionBuilder: TransactionBuilder,
    serviceHub: ServiceHub,
    fulfilledPaths: Map<StateRef, StateRefOrRef>): List<Int> {
        return listOf(1)
    }

fun fulfillKeyControlContract(
    contractStateAndRef: StateAndRef<KeyControlState>,
    transactionBuilder: TransactionBuilder,
    serviceHub: ServiceHub,
    fulfilledPaths: Map<StateRef, StateRefOrRef>): List<Int> {
        return listOf(1)
    }

fun fulfillByNewContract(
    contractStateAndRef: StateAndRef<FulfilledByNewContract>,
    transactionBuilder: TransactionBuilder,
    serviceHub: ServiceHub,
    fulfilledPaths: Map<StateRef, StateRefOrRef>): List<Int> {
        return listOf(1)
    }