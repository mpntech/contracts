package com.tradeix.contractcomposition.contracts.common

/**
 * Marker interface so that contracts can know whether the reference inputs in a fulfill command are in fact allowed
 * to be fulfilled as a ref input
 *
 * We might need to change this so that it's the Contracts rather than the States which implement this.
 */

interface FulfillableAsRefInput