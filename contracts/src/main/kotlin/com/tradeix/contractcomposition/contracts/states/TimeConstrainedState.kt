package com.tradeix.contractcomposition.contracts.states

import com.tradeix.contractcomposition.contracts.TimeConstrainedContract
import com.tradeix.contractcomposition.contracts.common.FulfillableAsRefInput
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import java.time.LocalDateTime

@BelongsToContract(TimeConstrainedContract::class)
data class TimeConstrainedState(
        val time: LocalDateTime,
        // "BEFORE" or "AFTER"
        val type: String,
        override val participants: List<AbstractParty>
): ContractState, FulfillableAsRefInput