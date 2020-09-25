package com.tradeix.contractcomposition.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.tradeix.contractcomposition.contracts.KeyControlContract
import com.tradeix.contractcomposition.contracts.TimeConstrainedContract
import com.tradeix.contractcomposition.contracts.commands.CreateBasicContractsCommand
import com.tradeix.contractcomposition.contracts.common.StateRefOrRef
import com.tradeix.contractcomposition.contracts.states.*
import net.bytebuddy.asm.Advice
import net.corda.core.contracts.Amount
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

/**
 * Example flow that issues a "funding response" in a receivables finance orchestration.
 *
 * A funding response can be best thought of as two contingent obligations chained together.
 *
 * An obligation is itself best understood as some collateral (either tangible in the case of another on-chain asset
 * or intangible as with some state which could be used in an off-chain legal system) whose ownership is ambiguous.
 * The "obligor" may become the sole and rightful owner if they do their part (i.e. make a payment) and the "obligee"
 * takes control (or at least maintains the option) if the obligor chooses not to do their thing.
 *
 * A contingent obligation is simply one where the obligee's right to take collateral is only realized if some other
 * actions happen first.
 *
 * So, back to the funding response: there is first the contingent obligation for the supplier to pay the funder the
 * full amount of the invoices being financed. If a supplier makes this payment, they can take control over the
 * "supplier neglected to pay" state (so that they can destroy it). However, the supplier is unlikely to make that
 * payment if they don't think anyone else has the right to it.
 *
 * The way that someone else could get a right to the "supplier neglected to pay" state is the contingent bit.
 * Specifically, if the funder makes the required trade payment AND it is now past settlement date AND there is proof
 * the supplier agreed to the deal, funder can take the collateral. So, the willingness of the funder to make that
 * payment is also contingent on issuance upon whether or not the supplier accepts their offer. This is also important
 * from the standpoint that if the supplier accepts the deal and the funder refuses to pay, they can become the owner
 * of the "funder neglected to pay" state.
 *
 * I expect that we will want to abstract a lot of what is in this flow away from developers writing flows in future
 * (e.g. with a contract composition DSL or something similar), but for now this shows what happens under the hood.
 *
 * Note that this flow currently does not support partial invoice re-payments are not possible. We can add this later.
 */

class ExampleFlow(
        val sessions: List<FlowSession>,
        val purchaseAmount: Amount<Currency>,
        val expirationDateTime: LocalDateTime,
        // Note that it would currently be fairly difficult to make tradePaymentDueDate to be something like
        // "two days after supplier accepts" because the state which governs the date eeds to be added when
        // FR is issued not when supplier accepts. We can find a workaround but it would not currently be supported
        val tradePaymentDueDateTime: LocalDateTime,
        // Map.Entry is the same as Pair
        val repaymentSchedule: List<Map<Amount<Currency>, LocalDateTime>>,
        val funder: AbstractParty,
        val supplier: AbstractParty
): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // no notary - will be a lot going on but nothing gets consumed
        val transactionBuilder = TransactionBuilder(notary = null)

        // We start by adding the KeyControlState whose fulfillment will represent supplier's acceptance of the deal.
        // Funder included as participant on the state because they need to know when the supplier accepts.
        // Note: No command added. Will add all commands at end such that we add only one per contract type
        transactionBuilder.addOutputState(
                state = KeyControlState(
                        key = supplier,
                        participants = listOf(supplier, funder)))

        // Next, we'll set up a Composite contract which can only be fulfilled if the previous KeyControl is also
        // fulfilled AND it's before the expirationDateTime AND what we can call a conflict resolution state is consumed
        // as an input.

        // Fulfillment of this contract will allow supplier to hold funder accountable later on if they
        // decide not to pay; the mechanism by which they do this will be configured is in a later step.
        // The contract resolution state is simply a Multipath state which will be pointed to by competing contracts.

        // In this case, the funder can cancel a response up until the supplier has accepted it. So to prevent a supplier
        // from accepting and funder from canceling we simply let those contracts race over a Multipath.

        // To do this, we will start by adding all the contracts the composite contract will point to, though we could
        // just as well start from the other way around if we wanted. Building from ground up, this seems more intuitive.

        // After expiration, Supplier will not be able to accept the response.
        transactionBuilder.addOutputState(
                state = TimeConstrainedState(
                        time = expirationDateTime,
                        type = "BEFORE",
                        participants = listOf(supplier, funder)))
        // Double spend prevention requires that the Multipath is consumed
        transactionBuilder.addOutputState(
                state = DoubleSpendPreventionState(
                        statesToNotarize = listOf(StateRefOrRef(ref = 3)),
                        participants = listOf(supplier, funder)))
        // The Multipath requires that the contract which governs the supplier acceptance (added next, index 4) runs
        // successfully or that which governs the funder cancellation (added in a few steps, index XXXXX) runs successfully.
        // Because the previous state enforces that the Multipath is used only once, we prevent funder cancellation and
        // supplier acceptance from both happening at same time, as discussed already.

        // The second state in the Multipath contracts list will be the cancellation contract we add later.
        transactionBuilder.addOutputState(
                state = MultipathContractState(
                        contracts = listOf(StateRefOrRef(ref = 4), StateRefOrRef(ref = 10)),
                        participants = listOf(supplier, funder)))

        // Now we add the actual composite contract that ties this together. Fulfillment of this contract means the
        // supplier is accepting the deal. As of this step, it only means that because I say it means that. The step
        // after this will give the fulfillment of this contract some meaning in that supplier will be able to use proof
        // of fulfillment to take some other action...which also only has meaning because I say it does :-)
        transactionBuilder.addOutputState(
                state = CompositeContractState(
                        // Composite contract points to the 4 previously added states
                        contracts = listOf(StateRefOrRef(ref = 0), StateRefOrRef(ref = 1), StateRefOrRef(ref = 2), StateRefOrRef(ref = 3)),
                        participants = listOf(supplier, funder)))

        // Next, as promised, we need to give the fact that supplier accepts the deal some staying power.
        // That is, we'll create the first path of three for the Multipath which will govern the control of the funder's
        // "collateral" - which will be added later but will be simply as a "Funder breached contract" state.

        // That first path to control the state which can prove the funder acting out of contract will be another
        // CompositeContractState. This CompositeContractState will be fulfillable if the supplier signs (KeyControlState)
        // AND it's after the trade payment due date (TimeConstrainedState) AND there is proof that the supplier accepted
        // the deal in the first place (DetachedContractState). It's that last one which brings our previously added
        // Composite state into play. If Supplier can prove they accepted the deal by proving they fulfilled that contract,
        // they can take for themselves the "funder never paid" collateral. In future, byt the way, that might actually
        // allow them to then do something else on-chain (like open a digitally native court case!)

        // Again, we will start by adding the sub-contracts and then create the Composite to point to those contracts.
        transactionBuilder.addOutputState(
                state = KeyControlState(
                        key = supplier,
                        participants = listOf(supplier, funder)))
        transactionBuilder.addOutputState(
                state = TimeConstrainedState(
                        time = tradePaymentDueDateTime,
                        type = "AFTER",
                        participants = listOf(supplier, funder)))
        // DetachedContractState which needs proof the acceptance contract ran (index 4 added above).
        // Notice that it does not need the proof to also be fulfilled which means the proof can be a normal ref input
        transactionBuilder.addOutputState(
                state = DetachedContractState(
                        requiredFulfilledId = StateRefOrRef(ref = 4),
                        participants = listOf(supplier, funder)))

        // Now add the Composite which is fulfillable by fulfillment of the 3 above
        // Again, although this contract doesn't mean anything yet, we will soon configure the funder's collateral
        // to be controlled by 3 paths, one of which will be this: proof of failed payment after supplier accepted deal.
        transactionBuilder.addOutputState(
                state = CompositeContractState(
                        contracts = listOf(StateRefOrRef(ref = 5), StateRefOrRef(ref = 6), StateRefOrRef(ref = 7)),
                        participants = listOf(supplier, funder)))

        // However, before we add that collateral, we need to add the two other paths it can take...
        // The first one we'll add was already mentioned. That is, the funder can cancel the deal and take back control
        // of the collateral if they choose to do so before the supplier accepts the deal. TODO: check if we need to make that configurable. Perhaps a funder wants to cancel after acceptance but before trade pay...

        // To set this upp, we will add yet another CompositeContractState. This time, though, we have two of the
        // three sub-contracts in the tx builder already. Those are, the DoubleSpendPreventionState and the Multipath
        // which needs to be consumed to satisfy the DoubleSpendPrevention and which mandates the cancellation contract
        // (the one we are about to add) OR the acceptance contract (first Composite we added) must be fulfilled.

        // The only other one we need to add is the Funder KeyControl state so that they can be the only ones to cancell
        // the deal (yet again, provided they do so before the supplier accepts as mandated by the other states...)
        // As has been the pattern, we'll add that one and then add the Composite which requires it
        transactionBuilder.addOutputState(
                state = KeyControlState(
                        key = funder,
                        participants = listOf(supplier, funder)))

        // Now we add the cancellation contract, fulfillable if the funder signs AND the supplier hasn't already accepted
        transactionBuilder.addOutputState(
                state = CompositeContractState(
                        contracts = listOf(StateRefOrRef(ref = 2), StateRefOrRef(ref = 3), StateRefOrRef(ref = 9)),
                        participants = listOf(supplier, funder)))

        // We can almost add the funder-side collateral now...just one more path (in form of CompositeContractState, of course)
        // This last one might be obvious. Before the supplier agrees to enter the deal, the funder can take back their
        // stake by simply canceling the deal (as we've seen). Once supplier has accepted, however, the only way funder
        // can get their collateral again is by actually making the payment they agreed to make. So, this Composite is
        // going to mandate that the funder "give" the supplier some claim that they made a payment of the correct amount
        // (FulfilledByNewContractState) AND that there is proof that the supplier actually agreed to receive the money
        // (again, DetachedContractState). We could make the second one optional except that we don't want the funder
        // to be able to say that they own the invoices because they made a payment if the supplier didn't actually
        // agree to it! So they're welcome to pay the supplier whenever they want, but they're only going to be able to
        // meet the trade payment contract if they do that alongside proof the supplier agreed to receive those funds.
        // Yet again, meeting the trade payment contract we are about to add means nothing at this stage, but will become
        // critically important later

        // Starting with the sub-contracts...

        // Contract fulfilled if there's proof the supplier accepted
        transactionBuilder.addOutputState(
                state = DetachedContractState(
                        requiredFulfilledId = StateRefOrRef(ref = 4),
                        participants = listOf(supplier, funder)))
        // Contract fulfilled if funder issues a claim that they paid the supplier the agreed amount off-chain...
        transactionBuilder.addOutputState(state = FulfilledByNewContract(
                newContract = DummyPaymentState(
                        payor = funder, payee = supplier, amountPaid = purchaseAmount, data = ""),
                participants = listOf(supplier, funder)))

        // Now the CompositeContractState, "trade payment contract"
        transactionBuilder.addOutputState(
                state = CompositeContractState(
                        contracts = listOf(StateRefOrRef(ref = 11), StateRefOrRef(ref = 12)),
                        participants = listOf(supplier, funder)))

        // We can finally add the Multipath that will, together with a DoubleSpendPreventionState, control the funder's
        // side of the collateral. We use the DoubleSpendPrevention in conjunction with the Multipath so that, for example,
        // if the supplier attempts to grab the collateral because it has passed due date at the same time that the funder
        // is attempting to grab the same because they are finally making the payment, someone needs to lose.

        // So, Multipath first which will point to the three paths already discussed (funder cancels, supplier accepts
        // and then waits until after trade payment due date has come and gone, or funder makes payment after supplier accepts).
        transactionBuilder.addOutputState(
                state = MultipathContractState(
                        contracts = listOf(StateRefOrRef(ref = 8), StateRefOrRef(ref = 10), StateRefOrRef(ref = 13)),
                        participants = listOf(supplier, funder)))
        // Now the DSP - requires the previous Multipath to be in inputs list
        transactionBuilder.addOutputState(
                state = DoubleSpendPreventionState(
                        statesToNotarize = listOf(StateRefOrRef(ref = 14)),
                        participants = listOf(supplier, funder)))

        // Lastly, before we move onto the supplier's skin in the game, we add the funder collateral. Unfortunately,
        // this contract has no meaning at this stage and it has no meaning at any other stage. By that I mean control of this state
        // does not grant the entity who controls it the right to do anything else on-chain. Presumably, having this state
        // may prove something in an out-of-system judicial system, but that's of no interest to what we're modelling in this system.
        // Eventually, we'll be able to replace this state with another which does have value. This is important to make
        // note of as it the line between our world and the outside. If Corda wins, the number of times we need to have this
        // line anywhere at all starts to decrease drastically...
        transactionBuilder.addOutputState(
                state = ArbitraryCompositeContractState(
                        data = "Funder didn't make the agreed payment!",
                        // This placeholder collateral is controlled by the Multipath and DSP added just previously
                        contracts = listOf(StateRefOrRef(ref = 14), StateRefOrRef(ref = 15)),
                        participants = listOf(supplier, funder)))


        // We're halfway through and through the more complex half.
        // We've created a contingent obligation for funder to make a payment if supplier accepts the terms of the deal.
        // Now, we need to set up the obligation for the supplier to pay the funder contingent upon BOTH the supplier
        // accepting the deal in the first place AND the funder making the payment.

        // Conveniently, we already have a contract which can be considered fulfilled only when those two events occur.
        // That is, the "trade payment" composite contract (index 13). Recall that I highlighted above that this contract would be
        // especially important. It is especially important because it plays a tole in a path to BOTH portions of collateral
        // we have in a funding response. We've already seen that if supplier accepts and funder pays, funder can use
        // proof of that trade payment contract running successfully to take back control of the collateral they put up.
        // Now, we're going to say that the funder can also use the same proof that supplier accepted and they paid to take the
        // collateral the SUPPLIER put up if they refuse to make the repayments by the designated time.

        // The way we do that is the same as the way we configured our funder's collateral. We're going to have some
        // arbitrary state which will represent the supplier collateral and it will be controlled by multiple paths
        // of fulfillment. One of those paths of fulfillment will be the successful fulfillment of a CompositeContractState
        // which itself can only be fulfilled if there is proof that supplier accepted the deal and funder paid
        // (DetachedContractState) AND it is after the date supplier was supposed to have returned the funds
        // (TimeConstrainedState) AND the funder signs the transaction. In less technical terms, all we're saying is that
        // if supplier agrees to the financing and then when maturity comes they don't pay the funder back, the funder
        // can hold them accountable.

        // The other path of supplier collateral control will mirror the trade payment path on funder collateral. That is,
        // if supplier pays before the funder takes the collateral (which they can only do after supplier misses due date)
        // then they can take the collateral back for themselves.

        // And those are going to be the only two paths as there is no way for the supplier or funder to cancel the deal
        // after funds have already moved to the supplier.

        // Because there are going to be different days and different amounts for each of those days the supplier must
        // pay back, we will do this whole process FOR EACH of our repaymentSchedule mappings.

        // So, our for loop needs to know how many outputs are currently in the list of outputs in order to configure each
        // new contract correctly. So we'll set that here to the number it is at the moment and add to it as we go

        // IMPORTANT: we could just as well make each of these separate transactions if the transaction size gets too
        // large. The only thing we would need to change is that any reference to the trade payment contract would be
        // by StateRef rather than output index (i.e. StateRef in StateRefOrRef != null - the rest would work the same)
        var currentOutputsSize = transactionBuilder.outputStates().size
        for (scheduledRepayment in repaymentSchedule) {
            // As always, then, we'll start to add in the supplier-side collateral by adding those sub-contracts first.
            // First, the requirement that funder sign
            transactionBuilder.addOutputState(
                    state = KeyControlState(
                            key = funder,
                            participants = listOf(supplier, funder)))
            // Next, restriction on when funder can act on this right
            transactionBuilder.addOutputState(
                    state = TimeConstrainedState(
                            time = scheduledRepayment.values.first(),
                            type = "AFTER",
                            participants = listOf(supplier, funder)))
            // proof that supplier agreed to the deal and funder made trade payment
            transactionBuilder.addOutputState(
                    state = DetachedContractState(
                            requiredFulfilledId = StateRefOrRef(ref = 13),
                            participants = listOf(supplier, funder)))

            // Now the Composite which points to those three
            // Since size is one more than the last index of the output list, first contract added will just have index of currentOutputsSize
            transactionBuilder.addOutputState(
                    state = CompositeContractState(
                            contracts = listOf(StateRefOrRef(ref = currentOutputsSize), StateRefOrRef(ref = currentOutputsSize + 1), StateRefOrRef(ref = currentOutputsSize + 2)),
                            participants = listOf(supplier, funder)))

            // Now we configure the other path to supplier collateral control: Supplier makes a payment

            // First, the FulfilledByNewContract which requires a claim that supplier made payment off-chain
            transactionBuilder.addOutputState(
                    state = FulfilledByNewContract(
                            newContract = DummyPaymentState(
                                    payor = supplier,
                                    payee = funder,
                                    // As mentioned in beginning, payment must be made in full for now
                                    amountPaid = scheduledRepayment.keys.first(),
                                    data = "",
                                    participants = listOf(supplier, funder)
                            ),
                            participants = listOf(supplier, funder)))
            // And the Composite which points to that FulfilledByNewContract (currentOutputSize + 4 as first Composite will be current + 3)
            transactionBuilder.addOutputState(
                    state = CompositeContractState(
                            contracts = listOf(StateRefOrRef(ref = currentOutputsSize + 4)),
                            participants = listOf(supplier, funder)))

            // Now the Multipath which will require one of the two Composites we've already added.
            transactionBuilder.addOutputState(
                    state = MultipathContractState(
                            contracts = listOf(StateRefOrRef(ref = currentOutputsSize + 3), StateRefOrRef(ref = currentOutputsSize + 5)),
                            participants = listOf(supplier, funder)))

            // And a DoubleSpendPrevention which will ensure that only one of the two paths are used to take collateral
            transactionBuilder.addOutputState(
                    state = DoubleSpendPreventionState(
                            statesToNotarize = listOf(StateRefOrRef(ref = currentOutputsSize + 6)),
                            participants = listOf(supplier, funder)))

            // And finally, the supplier collateral itself which requires that: ((the supplier pays OR the funder proves
            // that the supplier accepted the deal but now has not paid) AND (the Multipath which controls those options
            // is consumed as an input))
            transactionBuilder.addOutputState(
                    state = ArbitraryCompositeContractState(
                            data = "Supplier didn't pay ${scheduledRepayment.keys.first()}",
                            contracts = listOf(StateRefOrRef(ref = currentOutputsSize + 6), StateRefOrRef(ref = currentOutputsSize + 7)),
                            participants = listOf(supplier, funder)))

            // currentOutputsSize now 9 more than it was since we just added 8 additional output states
            currentOutputsSize += 9
        }

        // We've now added all outputs states which are required to construct a funding response
        // Although there isn't actually any checks on any of these particular states when they're only being created,
        // we still need to tell each contract that they are only being created. We should be able to do this with one
        // command...
        transactionBuilder.addCommand(CreateBasicContractsCommand(), listOf(funder.owningKey))

        // Only funder will sign, if Supplier isn't interested, they'll just ignore the new response
        val signedTx = serviceHub.signInitialTransaction(transactionBuilder, funder.owningKey)

        return subFlow(FinalityFlow(signedTx, sessions))
    }

}