package com.lumedic.network.base.flow

import co.paralleluniverse.fibers.Suspendable
import com.lumedic.network.base.model.PayerCTP
import com.lumedic.network.base.state.HARState

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

// Simple flow that requests the Nth prime number from the specified oracle.

@InitiatingFlow
class QueryAuthRequired(val oracle: Party, val payerCTPinstance: PayerCTP) : FlowLogic<PayerCTP>() {
    @Suspendable override fun call() = initiateFlow(oracle).sendAndReceive<PayerCTP>(payerCTPinstance).unwrap{ it }
}

@InitiatingFlow
class QueryAuthCode(val payer: Party, val harState: HARState) : FlowLogic<HARState>() {
    @Suspendable override fun call() = initiateFlow(payer).sendAndReceive<HARState>(harState).unwrap{ it }
}