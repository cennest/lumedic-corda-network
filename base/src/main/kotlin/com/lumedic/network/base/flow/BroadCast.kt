package com.lumedic.network.base.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap


object BroadCast {

    /**
     * Filters out any notary identities and removes our identity, then broadcasts the [SignedTransaction] to all the
     * remaining identities.
     * To call this flow :-  subFlow(BroadCast.BroadcastTransaction(signedTx))
     */
    @InitiatingFlow
    class BroadcastTransaction(private val stx: SignedTransaction) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            // Get a list of all identities from the network map cache.
            val everyone = serviceHub.networkMapCache.allNodes.flatMap { it.legalIdentities }

            // Filter out the notary identities and remove our identity.
            val everyoneButMeAndNotary = everyone.filter { serviceHub.networkMapCache.isNotary(it).not() } - ourIdentity

            // Create a session for each remaining party.
            val sessions = everyoneButMeAndNotary.map { initiateFlow(it) }

            // Send the transaction to all the remaining parties.
            sessions.forEach { subFlow(SendTransactionFlow(it, stx)) }
        }
    }


    /**
     * Other side of the [BroadcastTransaction] flow. It uses the observable states feature. When [ReceiveTransactionFlow]
     * is called, the [StatesToRecord.ALL_VISIBLE] parameter is used so that all the states are recorded despite the
     * receiving node not being a participant in these states.
     *
     * WARNING: This feature still needs work. Storing fungible states, like cash when you are not the owner will cause
     * problems when using [generateSpend] as the vault currently assumes that all states in the vault are spendable. States
     * you are only an observer of are NOT spendable!
     */
    @InitiatedBy(BroadcastTransaction::class)
    class RecordTransactionAsObserver(private val otherSession: FlowSession) :FlowLogic<Unit>() {

        @Suspendable
        override fun call() {

            // Receive and record the new campaign state in our vault EVEN THOUGH we are not a participant as we are
            // using 'ALL_VISIBLE'.
            val flow = ReceiveTransactionFlow(
                    otherSideSession = otherSession,
                    checkSufficientSignatures = true,
                    statesToRecord = StatesToRecord.ALL_VISIBLE
            )

            subFlow(flow)
        }
    }
}