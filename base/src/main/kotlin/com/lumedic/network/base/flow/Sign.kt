package com.lumedic.network.base.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.unwrap

import net.corda.core.transactions.SignedTransaction


// Simple flow which takes a filtered transaction (exposing only a command containing the nth prime data) and returns
// a digital signature over the transaction Merkle root.
@InitiatingFlow
class SignFilteredTransaction(val oracle: Party, val ftx: FilteredTransaction) : FlowLogic<TransactionSignature>() {
    @Suspendable override fun call(): TransactionSignature {
        val session = initiateFlow(oracle)
        return session.sendAndReceive<TransactionSignature>(ftx).unwrap { it }
    }
}

@InitiatingFlow
class GetSignedTransaction(val signingParty: Party, val partlySignedTransaction: SignedTransaction) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val session = initiateFlow(signingParty)
        return session.sendAndReceive<SignedTransaction>(partlySignedTransaction).unwrap { it }
    }
}