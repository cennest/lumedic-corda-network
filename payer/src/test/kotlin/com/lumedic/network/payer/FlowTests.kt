package com.lumedic.network.payer

import com.lumedic.network.base.contract.HARSTATE_CONTRACT_ID
import com.lumedic.network.base.contract.HARStateContract
import com.lumedic.network.base.flow.GetSignedTransaction
import com.lumedic.network.base.flow.QueryAuthCode
import com.lumedic.network.base.model.HARStatus
import com.lumedic.network.base.state.HARState
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class FlowTests {
    private lateinit var mockNet: MockNetwork
    lateinit var nodePayer: StartedMockNode
    lateinit var nodeProvider: StartedMockNode
    lateinit var nodeOracle: StartedMockNode
    lateinit var nodeNotary: StartedMockNode

    @Before
    fun setup() {
        mockNet = MockNetwork(listOf("com.lumedic.network.base", "com.lumedic.network.payer", "com.lumedic.network"), threadPerNode = false)
        nodePayer = mockNet.createPartyNode(CordaX500Name("Payer", "London", "GB"))
        nodeOracle = mockNet.createPartyNode(CordaX500Name("Oracle", "New York", "US"))
        nodeNotary = mockNet.createPartyNode(CordaX500Name("Notary", "London", "GB"))
        nodeProvider = mockNet.createPartyNode(CordaX500Name("Provider", "London", "GB"))
        registerFlowsAndServices(nodePayer)
    }

    fun registerFlowsAndServices(node: StartedMockNode) {
        node.registerInitiatedFlow(ProposalFlow.ApprovalCodeHandler::class.java)
    }

    fun StartedMockNode.legalIdentity(): Party {
        return this.info.legalIdentities.first()
    }

    @After
    fun tearDownNetwork() {
        mockNet.stopNodes()
    }

    companion object {
        //val logger: Logger = loggerFor<FlowTest>()
    }

    fun LocalDateTime.toDate(): Date {
        val zdt = ZonedDateTime.of(this, ZoneId.systemDefault())
        val cal = GregorianCalendar.from(zdt)
        return cal.time
    }

    fun randomInt(min: Int, max: Int): Int {
        if (min > max || max - min + 1 > Int.MAX_VALUE) throw IllegalArgumentException("Invalid Range")
        return Random().nextInt(max - min + 1) + min
    }

    fun randomDate(): Date {
        val ldt = LocalDateTime.now().plusDays(randomInt(0, 30 * 4).toLong())
        return ldt.toDate()
    }


    data class EpicRecord(val payer: String, val desc: String, val cptCode: String, var scheduledDate: Date? = null)

    @Test
    fun testApprovalCodeHandlerCPTCodeExists() {
        val harID = "10000"
        val epicRecord = EpicRecord("PNP", "Oncology (colorectal)", "0002U", scheduledDate = randomDate())
        val uniqueIdentifier = UniqueIdentifier(harID)
        val harState = HARState(HARStatus.PENDING.toString(), listOf(nodeProvider.legalIdentity()), epicRecord.desc, harID, epicRecord.cptCode, "", epicRecord.payer, "", "", uniqueIdentifier, epicRecord.scheduledDate!!)


        val flow = QueryAuthCode(nodePayer.legalIdentity(), harState)
        val future = nodePayer.startFlow(flow)
        mockNet.runNetwork()
        val state = future.get()

        assertNotEquals("NA",state.authCode, "Auth code mis-matched")
        assertEquals("PNP", state.payer, "Payer mis-matched")
        assertEquals("0002U",state.cptCode,"CPTCode mis-matched")
        assertEquals("Oncology (colorectal)",state.description, "Epic description mis-matched")
        assertEquals(HARStatus.PENDING.toString(), state.status, "HARStatus mis-matched")
        assertEquals(uniqueIdentifier, state.linearId, "linearId mis-matched")
        assertEquals(epicRecord.scheduledDate, state.eventTime, "eventTime mis-matched")
    }

    @Test
    fun testApprovalCodeHandlerCPTCodeNotExists() {
        val harID = "10000"
        val epicRecord = EpicRecord("PNP", "Oncology (colorectal)", "000FU", scheduledDate = randomDate())
        val uniqueIdentifier = UniqueIdentifier(harID)
        val harState = HARState(HARStatus.PENDING.toString(), listOf(nodeProvider.legalIdentity()), epicRecord.desc, harID, epicRecord.cptCode,"", epicRecord.payer,"", "", uniqueIdentifier, epicRecord.scheduledDate!!)

        val flow = QueryAuthCode(nodePayer.legalIdentity(), harState)
        val future = nodePayer.startFlow(flow)
        mockNet.runNetwork()
        val state = future.get()

        assertEquals("NA",state.authCode, "Auth code mis-matched")
        assertEquals("PNP", state.payer, "Payer mis-matched")
        assertEquals("000FU",state.cptCode,"CPTCode mis-matched")
        assertEquals("Oncology (colorectal)",state.description, "Epic description mis-matched")
        assertEquals(HARStatus.PENDING.toString(), state.status, "HARStatus mis-matched")
        assertEquals(uniqueIdentifier, state.linearId, "linearId mis-matched")
        assertEquals(epicRecord.scheduledDate, state.eventTime, "eventTime mis-matched")
    }

    @Test
    fun testAcceptor() {
        val harID = "10000"
        val epicRecord = EpicRecord("PNP", "Oncology (colorectal)", "000FU", scheduledDate = randomDate())
        val uniqueIdentifier = UniqueIdentifier(harID)
        val inputState = HARState(HARStatus.PENDING.toString(), listOf(nodeProvider.legalIdentity()), epicRecord.desc, harID, epicRecord.cptCode,"", epicRecord.payer, "","", uniqueIdentifier, epicRecord.scheduledDate!!)

        val outputState = HARState(HARStatus.CONFIRMED.toString(), listOf(nodeProvider.legalIdentity(), nodePayer.legalIdentity()),
                inputState.description, harID, inputState.cptCode, "", inputState.payer,"", "authCode", uniqueIdentifier,
                inputState.eventTime, Date.from(Instant.now()), inputState.isAuthRequired)

        val txBuilder = TransactionBuilder(notary = nodeNotary.legalIdentity())
        val outputContractAndState = StateAndContract(outputState, HARSTATE_CONTRACT_ID)
        val cmd = Command(HARStateContract.MarkDeniedConfirmed(), listOf(nodePayer.legalIdentity().owningKey, nodeProvider.legalIdentity().owningKey))

        val inputStateRef: StateAndRef<HARState> = StateAndRef(TransactionState(inputState, HARSTATE_CONTRACT_ID, notary = nodeNotary.legalIdentity()),
                StateRef(SecureHash.zeroHash, 0))
        txBuilder.addInputState(inputStateRef)
        // We add the items to the builder.
        txBuilder.withItems(outputContractAndState, cmd)
        val ptx = nodeProvider.services.signInitialTransaction(txBuilder)

        val flow = GetSignedTransaction(nodePayer.legalIdentity(), ptx)
        val future = nodePayer.startFlow(flow)
        mockNet.runNetwork()
        val transaction = future.get()
        val signCount = transaction.sigs.count()

        val payerKey = nodePayer.legalIdentity().owningKey
        val payerSign = nodePayer.services.createSignature(ptx, payerKey)
        val payerSignContained = transaction.sigs.contains(payerSign)

        val providerKey = nodePayer.legalIdentity().owningKey
        val providerSign = nodePayer.services.createSignature(ptx, providerKey)
        val providerSignContained = transaction.sigs.contains(providerSign)

        assert(signCount == 2)
        assert(payerSignContained)
        assert(providerSignContained)

        assertEquals("000FU",inputState.cptCode,"CPTCode mis-matched")
        assertEquals("000FU",outputState.cptCode,"CPTCode mis-matched")

        assertEquals("Oncology (colorectal)",inputState.description, "Epic description mis-matched")
        assertEquals("Oncology (colorectal)",outputState.description, "Epic description mis-matched")

        assertEquals(HARStatus.PENDING.toString(), inputState.status, "HARStatus mis-matched")
        assertEquals(HARStatus.CONFIRMED.toString(), outputState.status, "HARStatus mis-matched")

        assertEquals(uniqueIdentifier, inputState.linearId, "linearId mis-matched")
        assertEquals(uniqueIdentifier, outputState.linearId, "linearId mis-matched")

        assertEquals(epicRecord.scheduledDate, inputState.eventTime, "eventTime mis-matched")
        assertEquals(epicRecord.scheduledDate, outputState.eventTime, "eventTime mis-matched")
    }
}