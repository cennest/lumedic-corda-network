package com.lumedic.network.payer

import co.paralleluniverse.fibers.Suspendable

import com.lumedic.network.base.flow.GetSignedTransaction
import com.lumedic.network.base.flow.QueryAuthCode

import com.lumedic.network.payer.db.PayerDbService

import com.lumedic.network.base.state.HARState
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

object ProposalFlow {

    @InitiatedBy(QueryAuthCode::class)
    class ApprovalCodeHandler(val session: FlowSession) : FlowLogic<Unit>() {
        companion object {
            object RECEIVING : ProgressTracker.Step("Receiving query request.")
            object CALCULATING : ProgressTracker.Step("Getting the auth code")
            object SENDING : ProgressTracker.Step("Sending query response.")
        }

        override val progressTracker = ProgressTracker(RECEIVING, CALCULATING, SENDING)

        @Suspendable
        override fun call() {
            progressTracker.currentStep = RECEIVING
            val request = session.receive<HARState>().unwrap { it }

            progressTracker.currentStep = CALCULATING

            val payerDbService = serviceHub.cordaService(PayerDbService::class.java)
            val authCode = payerDbService.getAuthorizationCodeForCPT(request.cptCode)

            val response = request.copy(authCode = authCode ?: "NA")

            progressTracker.currentStep = SENDING
            session.send(response)
        }
    }

    @InitiatedBy(GetSignedTransaction::class)
    class Acceptor(val session: FlowSession) :FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val request = session.receive<SignedTransaction>().unwrap { it }
            val key = this.serviceHub.myInfo.legalIdentities.first().owningKey
            val sign = this.serviceHub.createSignature(request, key)
            val signed = request.withAdditionalSignature(sign)
            if(!(request.tx.outputs.single().data is HARState))
            {
                throw FlowException(message="output should be a HARState")
            }
            return session.send(signed)
        }

    }
}