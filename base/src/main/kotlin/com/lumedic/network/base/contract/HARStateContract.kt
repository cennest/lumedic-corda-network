package com.lumedic.network.base.contract

import com.lumedic.network.base.model.HARStatus
import com.lumedic.network.base.state.HARState

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.Requirements.using
import net.corda.core.transactions.LedgerTransaction

const val HARSTATE_CONTRACT_ID = "com.lumedic.network.base.contract.HARStateContract"

class HARStateContract: Contract
{

    class ReceiveHARData : CommandData
    class CheckAuth(val cptCode:String, val payer:String,val isAuthRequired:Boolean?): CommandData
    class MarkDeniedConfirmed:CommandData



    override fun verify(tx: LedgerTransaction) {

        val command= tx.commands.first()
        when(command.value) {
            is ReceiveHARData -> {

                "No initial inputs" using (tx.inputStates.isEmpty())
                "One output" using (tx.outputStates.size == 1)

                val outputState = tx.outputsOfType<HARState>().single()
                "Output state should be Open" using (outputState.status == HARStatus.OPEN.toString())

                "Event date should not be empty" using (!outputState.eventTime.toString().isNullOrEmpty())
                "Event description should not be empty" using (!outputState.description.isNullOrEmpty())

                "Check number of participants sign required" using (command.signers.toSet().size == outputState.participants.size)
                "All signers parties must sign" using(command.signingParties.containsAll(outputState.participants))
            }

            is CheckAuth -> {

                "One input" using (tx.inputStates.size == 1)
                "One output" using (tx.outputStates.size == 1)

                val inputState = tx.inputsOfType<HARState>().single()
                "Input state should be Open" using (inputState.status == HARStatus.OPEN.toString())

                val outputState = tx.outputsOfType<HARState>().single()

                if(outputState.status == HARStatus.PENDING.toString()){
                    "Output state should set isAuthRequired to true" using (outputState.isAuthRequired == true)
                }

                if(outputState.status == HARStatus.CONFIRMED.toString()){
                    "Output state should set isAuthRequired to false" using (outputState.isAuthRequired == false)
                }

                "Check number of participants sign required" using (command.signers.toSet().size == outputState.participants.size)
                "All signers parties must sign" using(command.signingParties.containsAll(outputState.participants))

            }

            is MarkDeniedConfirmed -> {

                "One input" using (tx.inputStates.size == 1)
                "One output" using (tx.outputStates.size == 1)

                val inputState = tx.inputsOfType<HARState>().single()
                val outputState = tx.outputsOfType<HARState>().single()

                "Input & output state's isAuthRequired should not be changed" using (inputState.isAuthRequired == outputState.isAuthRequired)
                "Input state should not be OPEN" using (inputState.status != HARStatus.OPEN.toString())


                if(inputState.status == HARStatus.PENDING.toString()){
                    "Input state should set isAuthRequired to true" using (inputState.isAuthRequired == true)
                }

                if(outputState.status == HARStatus.DENIED.toString()){
                    "Output state should set isAuthRequired to true" using (outputState.isAuthRequired == true)
                    "Output state should set authCode to NA" using (outputState.authCode == "NA")
                }

                if(outputState.status == HARStatus.CONFIRMED.toString()){
                    "Output state should set isAuthRequired to true" using (outputState.isAuthRequired == true)
                    "Output state should set valid authCode" using (outputState.authCode != "NA" && !outputState.authCode.isNullOrEmpty())
                }

                "Check number of participants sign required" using (command.signers.toSet().size == outputState.participants.size)
                "All signers parties must sign" using(command.signingParties.containsAll(outputState.participants))

            }
        }
    }
}
