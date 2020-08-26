package com.tradeix.contractcomposition.contractbuilders.core

import com.tradeix.contractcomposition.contractbuilders.ContractOutline

class InOutline(val requiredContracts: List<ContractOutline>): ContractOutline, CoreOutline {

    override fun BuildContract(): ContractOutline {
        return this
    }
}