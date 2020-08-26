package com.tradeix.contractcomposition.contracts.commands

import net.corda.core.contracts.CommandData

/**
 * As a transaction can't have 0 commands, this command is available in case all we're doing in a given tx is issuance.
 * Our standard component contracts don't have any verification logic on issuance; our verify methods will therefore
 * return immediately when they encounter this command.
 */
class CreateBasicContractsCommand: CommandData