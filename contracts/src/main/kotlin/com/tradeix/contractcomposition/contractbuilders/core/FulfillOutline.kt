package com.tradeix.contractcomposition.contractbuilders.core

import com.tradeix.contractcomposition.contractbuilders.ContractOutline

class FulfillOutline(val contractToFulfill: ContractOutline): ContractOutline, CoreOutline {

    override fun BuildContract(): ContractOutline {
        return this
    }
}