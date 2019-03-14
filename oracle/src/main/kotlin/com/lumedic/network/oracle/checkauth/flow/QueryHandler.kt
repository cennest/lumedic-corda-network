package com.lumedic.network.oracle.checkauth.flow

import co.paralleluniverse.fibers.Suspendable
import com.lumedic.network.base.flow.QueryAuthRequired
import com.lumedic.network.base.model.PayerCTP
import com.lumedic.network.oracle.checkauth.service.Oracle
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

// The oracle flow to handle payer's authorization.
@InitiatedBy(QueryAuthRequired::class)
class QueryHandler(val session: FlowSession) : FlowLogic<Unit>() {
    companion object {
        object RECEIVING : ProgressTracker.Step("Receiving query request.")
        object CALCULATING : ProgressTracker.Step("Calculating Nth prime.")
        object SENDING : ProgressTracker.Step("Sending query response.")
    }

    override val progressTracker = ProgressTracker(RECEIVING, CALCULATING, SENDING)

    @Suspendable
    override fun call() {
        progressTracker.currentStep = RECEIVING
        val request = session.receive<PayerCTP>().unwrap { it }

        progressTracker.currentStep = CALCULATING
        val response = try {
            serviceHub.cordaService(Oracle::class.java).queryCPTCode(request)
        } catch (e: Exception) {
            // Re-throw the exception as a FlowException so its propagated to the querying node.
            throw FlowException(e)
        }

        progressTracker.currentStep = SENDING
        session.send(response)
    }
}