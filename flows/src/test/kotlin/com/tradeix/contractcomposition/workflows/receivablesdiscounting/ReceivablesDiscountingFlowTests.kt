package com.tradeix.contractcomposition.workflows.receivablesdiscounting

import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp

class ReceivablesDiscountingFlowTests {
    private val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.tradeix.contractcomposition.contracts"),
            TestCordapp.findCordapp("com.tradeix.contractcomposition.workflows.flows")
    )))

    private val funder = network.createNode()
    private val supplier = network.createNode()

}