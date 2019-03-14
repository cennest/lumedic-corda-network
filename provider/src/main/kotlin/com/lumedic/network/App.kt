package com.lumedic.network

import co.paralleluniverse.fibers.Suspendable
import com.lumedic.network.base.contract.HARSTATE_CONTRACT_ID
import com.lumedic.network.base.contract.HARStateContract
import com.lumedic.network.base.flow.*
import com.lumedic.network.base.model.EpicRecord
import com.lumedic.network.base.model.HARStatus
import com.lumedic.network.base.model.PayerCTP
import com.lumedic.network.base.state.HARState
import com.lumedic.network.node.NodeIdentity
import jdk.net.SocketFlow


import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Instant
import java.util.*
import java.util.function.Predicate


@InitiatingFlow
@StartableByRPC
@StartableByService
class RecieveFlow(private val harID:String, private val epicRecord: EpicRecord):FlowLogic<Unit>()
{

   companion object {
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new HAR.")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying model constraints.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
        object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        )

    }
    override val progressTracker = tracker()
    @Suspendable
    override fun call() {
        val notary = NodeIdentity.getNotary(serviceHub)
        val txBuilder = TransactionBuilder(notary = notary)
        progressTracker.currentStep = GENERATING_TRANSACTION
        val uniqueIdentifier= UniqueIdentifier(harID)

        val outputState = HARState(
                status = HARStatus.OPEN.toString(),
                parties =  listOf(ourIdentity),
                description =  epicRecord.desc,
                harID =  harID,
                cptCode =  epicRecord.cptCode,
                provider =  epicRecord.provider,
                payer =  epicRecord.payer,
                branch =  epicRecord.branch,
                authCode =  "",
                linearId =  uniqueIdentifier,
                eventTime =  epicRecord.scheduledDate!!)

        val outputContractAndState = StateAndContract(outputState, HARSTATE_CONTRACT_ID)
        val cmd = Command(HARStateContract.ReceiveHARData(), listOf(ourIdentity.owningKey))

        serviceHub.jdbcSession()

        // We add the items to the builder.
        txBuilder.withItems(outputContractAndState, cmd)
        progressTracker.currentStep = VERIFYING_TRANSACTION
        // Verifying the transaction.
        txBuilder.verify(serviceHub)
        progressTracker.currentStep = SIGNING_TRANSACTION

        // Signing the transaction.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        // progressTracker.currentStep = ScreenFlow.Companion.GATHERING_SIGS
        subFlow(FinalityFlow(signedTx))
    }
}

@InitiatingFlow
@StartableByRPC
@StartableByService
class CheckForAuthRequired(val harID: String) : FlowLogic<SignedTransaction>() {

    companion object {
    object SET_UP : ProgressTracker.Step("Initialising flow.")
    object QUERYING_THE_ORACLE : ProgressTracker.Step("Querying oracle for the authorization check.")
    object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
    object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
    object WE_SIGN : ProgressTracker.Step("signing transaction.")
    object ORACLE_SIGNS : ProgressTracker.Step("Requesting oracle signature.")
    object FINALISING : ProgressTracker.Step("Finalising transaction.") {
        override fun childProgressTracker() = FinalityFlow.tracker()
    }

    fun tracker() = ProgressTracker(SET_UP, QUERYING_THE_ORACLE, BUILDING_THE_TX,
            VERIFYING_THE_TX, WE_SIGN, ORACLE_SIGNS, FINALISING)
}

override val progressTracker = tracker()

@Suspendable
override fun call(): SignedTransaction {
    progressTracker.currentStep = SET_UP
    val notary = NodeIdentity.getNotary(serviceHub)
    val txBuilder = TransactionBuilder(notary = notary)

    val oracle = NodeIdentity.getPartyFromNetwork(serviceHub, NodeIdentity.Oracle)
    val uniqueIdentifier= UniqueIdentifier(harID)

    val criteria: QueryCriteria.LinearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria(externalId = listOf(harID))

    // default is UNCONSUMED
    val results: Vault.Page<HARState> = serviceHub.vaultService.queryBy<HARState>(criteria)
    val statesInVault: List<StateAndRef<HARState>> = results.states

    //take first state for now
    val inputState= statesInVault[0]
    progressTracker.currentStep = QUERYING_THE_ORACLE
    val payerCTP = PayerCTP(inputState.state.data.cptCode, inputState.state.data.payer)
    val filledCTP = subFlow(QueryAuthRequired(oracle, payerCTP))
    val status: HARStatus = if(filledCTP.isAuthRequired==false) HARStatus.CONFIRMED else HARStatus.PENDING
    progressTracker.currentStep = BUILDING_THE_TX



    // val outputState = ClaimsState("Screened", listOf(ourIdentity),inputState.state.data.name,inputState.state.data.age,inputState.state.data.medicalInfo,"","","",inputState.state.data.linearId)

    val outputState = HARState(
            status = status.toString(),
            parties = listOf(oracle, ourIdentity),
            description = inputState.state.data.description,
            harID = harID,
            cptCode = filledCTP.ctpCode,
            provider = inputState.state.data.provider,
            payer = inputState.state.data.payer,
            branch =  inputState.state.data.branch,
            authCode = inputState.state.data.authCode,
            linearId = uniqueIdentifier,
            eventTime = inputState.state.data.eventTime,
            updatedDate =  Date.from(Instant.now()),
            isAuthRequired =  filledCTP.isAuthRequired)

    val outputContractAndState = StateAndContract(outputState, HARSTATE_CONTRACT_ID)
    val cmd = Command(HARStateContract.CheckAuth(filledCTP.ctpCode,filledCTP.payer,isAuthRequired = filledCTP.isAuthRequired), listOf(oracle.owningKey, ourIdentity.owningKey))

    txBuilder.addInputState(inputState)
    // We add the items to the builder.
    txBuilder.withItems(outputContractAndState, cmd)


    //val primeState = PrimeState(index, nthPrimeRequestedFromOracle, ourIdentity)
    //val primeCmdData = PrimeContract.Create(index, nthPrimeRequestedFromOracle)
    // By listing the oracle here, we make the oracle a required signer.
    /*val primeCmdRequiredSigners = listOf(oracle.owningKey, ourIdentity.owningKey)
    val builder = TransactionBuilder(notary)
            .addOutputState(primeState, PRIME_PROGRAM_ID)
            .addCommand(primeCmdData, primeCmdRequiredSigners)*/
    progressTracker.currentStep = VERIFYING_THE_TX
    txBuilder.verify(serviceHub)

    progressTracker.currentStep = WE_SIGN
    val ptx = serviceHub.signInitialTransaction(txBuilder)

    progressTracker.currentStep = ORACLE_SIGNS
    // For privacy reasons, we only want to expose to the oracle any commands of type `Prime.Create`
    // that require its signature.
    val ftx = ptx.buildFilteredTransaction(Predicate {
        when (it) {
            is Command<*> -> oracle.owningKey in it.signers && it.value is HARStateContract.CheckAuth
            else -> false
        }
    })

    val oracleSignature = subFlow(SignFilteredTransaction(oracle, ftx))
    val stx = ptx.withAdditionalSignature(oracleSignature)

    progressTracker.currentStep = FINALISING
    return subFlow(FinalityFlow(stx))
}
}

@InitiatingFlow
@StartableByRPC
@StartableByService
class GetApprovalCode(val harID:String):FlowLogic<SignedTransaction>()
{
    companion object {
        object SET_UP : ProgressTracker.Step("Initialising flow.")
        object QUERYING_PAYER : ProgressTracker.Step("Querying payer for the authorization code.")
        object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
        object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
        object WE_SIGN : ProgressTracker.Step("signing transaction.")
        object PAYER_SIGNS : ProgressTracker.Step("Requesting payer signature.")
        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(SET_UP, QUERYING_PAYER, BUILDING_THE_TX,
                VERIFYING_THE_TX, WE_SIGN, PAYER_SIGNS, FINALISING)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = SET_UP
        val notary = NodeIdentity.getNotary(serviceHub)
        val txBuilder = TransactionBuilder(notary = notary)

        val payer = NodeIdentity.getPartyFromNetwork(serviceHub, NodeIdentity.Payer)
        val uniqueIdentifier= UniqueIdentifier(harID)


        val criteria: QueryCriteria.LinearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria(externalId = listOf(harID))

        // default is UNCONSUMED
        val results: Vault.Page<HARState> = serviceHub.vaultService.queryBy<HARState>(criteria)
        val statesInVault: List<StateAndRef<HARState>> = results.states

        //take first state for now
        val inputState= statesInVault[0]
        progressTracker.currentStep = QUERYING_PAYER
       // val payerCTP: PayerCTP = PayerCTP(inputState.state.data.cptCode,inputState.state.data.payer)
        val filledHAR = subFlow(QueryAuthCode(payer, inputState.state.data))
        val status: HARStatus = if(filledHAR.authCode!="NA") HARStatus.CONFIRMED else HARStatus.DENIED
        progressTracker.currentStep = BUILDING_THE_TX



        // val outputState = ClaimsState("Screened", listOf(ourIdentity),inputState.state.data.name,inputState.state.data.age,inputState.state.data.medicalInfo,"","","",inputState.state.data.linearId)

        val outputState = HARState(
                status =        status.toString(),
                parties =       listOf(ourIdentity, payer),
                description =   inputState.state.data.description,
                harID =         harID,
                cptCode =       inputState.state.data.cptCode,
                provider =      inputState.state.data.provider,
                payer =         inputState.state.data.payer,
                branch =        inputState.state.data.branch,
                authCode =      filledHAR.authCode,
                linearId =      uniqueIdentifier,
                eventTime =     inputState.state.data.eventTime,
                updatedDate =   Date.from(Instant.now()),
                isAuthRequired =filledHAR.isAuthRequired)

        val outputContractAndState = StateAndContract(outputState, HARSTATE_CONTRACT_ID)
        val cmd = Command(HARStateContract.MarkDeniedConfirmed() ,listOf(payer.owningKey, ourIdentity.owningKey))

        txBuilder.addInputState(inputState)
        // We add the items to the builder.
        txBuilder.withItems(outputContractAndState, cmd)


        //val primeState = PrimeState(index, nthPrimeRequestedFromOracle, ourIdentity)
        //val primeCmdData = PrimeContract.Create(index, nthPrimeRequestedFromOracle)
        // By listing the oracle here, we make the oracle a required signer.
        /*val primeCmdRequiredSigners = listOf(oracle.owningKey, ourIdentity.owningKey)
        val builder = TransactionBuilder(notary)
                .addOutputState(primeState, PRIME_PROGRAM_ID)
                .addCommand(primeCmdData, primeCmdRequiredSigners)*/
        progressTracker.currentStep = VERIFYING_THE_TX
        txBuilder.verify(serviceHub)

        progressTracker.currentStep = WE_SIGN
        val ptx = serviceHub.signInitialTransaction(txBuilder)

        progressTracker.currentStep = PAYER_SIGNS

        // Send the state to the counterparty, and receive it back with their signature.
        // val otherPartyFlow = initiateFlow(payer)
        // val fullySignedTx = subFlow(CollectSignaturesFlow(ptx, setOf(otherPartyFlow), RecieveFlow.Companion.GATHERING_SIGS.childProgressTracker()))

        val payerSigned = subFlow(GetSignedTransaction(payer, ptx))
        //val fullySignedTx = ptx.withAdditionalSignature(oracleSignature)
        // payerSigned.verify(this.serviceHub)

        progressTracker.currentStep = FINALISING
        return subFlow(FinalityFlow(payerSigned))
    }
    // return subFlow(FinalityFlow)
}

class SerializationWhiteList : SerializationWhitelist {

    override val whitelist: List<Class<*>> = listOf(java.sql.Date::class.java, java.util.Date::class.java,
            Instant::class.java, EpicRecord::class.java)

}
