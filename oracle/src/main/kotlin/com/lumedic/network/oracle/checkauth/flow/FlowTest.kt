package com.lumedic.network.oracle.checkauth.flow

import co.paralleluniverse.fibers.Suspendable
import com.lumedic.network.oracle.checkauth.http.RestService
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class TestFlow(): FlowLogic<Boolean>()
{
    companion object {
        object RECEIVING : ProgressTracker.Step("Receiving query request.")
        object SENDING : ProgressTracker.Step("Sending query response.")
        object FINISHED : ProgressTracker.Step("Finished query response.")
    }

    override val progressTracker = ProgressTracker(RECEIVING, SENDING, FINISHED)

    @Suspendable
    override fun call() : Boolean {

        //Debugging port
        //java -Dcapsule.jvm.args="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005" -jar corda.jar
        progressTracker.currentStep = TestFlow.Companion.RECEIVING

        val restService = serviceHub.cordaService(RestService::class.java)

        progressTracker.currentStep = TestFlow.Companion.SENDING
        var data = restService.getAuthorizationRequiredFlag("KnePH", "PHP")

        progressTracker.currentStep = TestFlow.Companion.FINISHED
        return data
    }
}