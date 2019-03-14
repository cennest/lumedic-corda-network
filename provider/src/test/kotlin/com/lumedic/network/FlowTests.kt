//package com.lumedic.network
//
//import co.paralleluniverse.fibers.Suspendable
//import com.lumedic.network.base.flow.GetSignedTransaction
//import com.lumedic.network.base.flow.QueryAuthCode
//import com.lumedic.network.base.flow.QueryAuthRequired
//import com.lumedic.network.base.flow.SignFilteredTransaction
//import com.lumedic.network.base.model.EpicRecord
//import com.lumedic.network.base.model.HARStatus
//import com.lumedic.network.base.model.PayerCTP
//import com.lumedic.network.base.state.HARState
//import com.lumedic.network.node.NodeIdentity
//import net.corda.core.contracts.StateAndRef
//import net.corda.core.identity.CordaX500Name
//import net.corda.core.identity.Party
//import net.corda.testing.node.MockNetwork
//import net.corda.testing.node.StartedMockNode
//import org.junit.After
//import org.junit.Before
//import org.junit.Test
//import java.time.LocalDateTime
//import java.util.*
//import net.corda.core.flows.FlowException
//import net.corda.core.flows.FlowSession
//import net.corda.core.flows.FlowLogic
//import net.corda.core.flows.InitiatedBy
//import net.corda.core.node.services.Vault
//import net.corda.core.node.services.queryBy
//import net.corda.core.node.services.vault.QueryCriteria
//import net.corda.core.transactions.FilteredTransaction
//import net.corda.core.transactions.SignedTransaction
//import net.corda.core.utilities.unwrap
//import java.time.ZoneId
//import java.time.ZonedDateTime
//import kotlin.test.assertEquals
//import kotlin.test.assertNotNull
//
//
//class FlowTests {
//    private lateinit var mockNet: MockNetwork
//    lateinit var nodePayer: StartedMockNode
//    lateinit var nodeProvider: StartedMockNode
//    lateinit var nodeOracle: StartedMockNode
//    lateinit var nodeNotary: StartedMockNode
//
//    @Before
//    fun setup() {
//        mockNet = MockNetwork(
//                listOf("com.lumedic.network.base",
//                       "com.lumedic.network.payer",
//                       "com.lumedic.network"),
//                threadPerNode = false)
//        nodePayer = mockNet.createPartyNode(NodeIdentity.Payer)
//        nodeProvider = mockNet.createPartyNode(NodeIdentity.Provider)
//        nodeOracle = mockNet.createPartyNode(NodeIdentity.Oracle)
//        nodeNotary = mockNet.createPartyNode(NodeIdentity.Notary)
//        //registerFlowsAndServices(nodeProvider)
//    }
//
//    fun registerFlowsAndServices(node: StartedMockNode) {
//
//    }
//
//    fun StartedMockNode.legalIdentity(): Party {
//        return this.info.legalIdentities.first()
//    }
//
//    @After
//    fun tearDownNetwork() {
//        mockNet.stopNodes()
//    }
//
//    companion object {
//        //val logger: Logger = loggerFor<FlowTest>()
//    }
//
//    fun LocalDateTime.toDate() : Date {
//        val zdt = ZonedDateTime.of(this, ZoneId.systemDefault())
//        val cal = GregorianCalendar.from(zdt)
//        return cal.time
//    }
//
//    fun randomInt(min: Int, max: Int) : Int {
//        if(min > max || max - min+1 > Int.MAX_VALUE) throw IllegalArgumentException("Invalid Range")
//        return Random().nextInt(max - min + 1) + min
//    }
//
//    fun randomDate() : Date {
//        val ldt = LocalDateTime.now().plusDays(randomInt(0, 30*4).toLong())
//        return ldt.toDate()
//    }
//
//    @InitiatedBy(QueryAuthRequired::class)
//    class DummyQueryHandler(val session: FlowSession) : FlowLogic<Unit>() {
//
//        @Suspendable
//        override fun call() {
//            val request = session.receive<PayerCTP>().unwrap { it }
//            val payers = listOf("PHP", "Aetna", "UHC")
//            request.isAuthRequired = !payers.contains(request.payer)
//            session.send(request)
//        }
//    }
//
//    @InitiatedBy(QueryAuthCode::class)
//    class DummyQueryAuthResponder(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
//
//        @Suspendable
//        @Throws(FlowException::class)
//        override fun call() {
//            val request = otherPartySession.receive<HARState>().unwrap { it }
//            val payers = listOf("PHP", "Aetna", "UHC")
//            val authCode = if(payers.contains(request.payer)) {
//                "OK"
//            } else {
//                "NA"
//            }
//
//            val response = request.copy(authCode = authCode)
//            otherPartySession.send(response)
//        }
//    }
//
//    @InitiatedBy(SignFilteredTransaction::class)
//    class DummySignHandler(val session: FlowSession) : FlowLogic<Unit>() {
//        @Suspendable
//        override fun call() {
//            val ftx = session.receive<FilteredTransaction>().unwrap { it }
//            val oracleNode = (serviceHub.networkMapCache.getNodeByLegalName(NodeIdentity.Oracle))!!
//            session.send(serviceHub.createSignature(ftx, oracleNode.legalIdentities.first().owningKey))
//        }
//    }
//
//    @Test
//    fun testReceiveFlow() {
//
//        val harId = "10000"
//
//        nodeProvider.startFlow(RecieveFlow( harID = harId,
//                epicRecord = EpicRecord("PHP","Oncology (colorectal)","0002U", scheduledDate = randomDate() )))
//        mockNet.runNetwork()
//
//        nodeProvider.transaction {
//            val criteria: QueryCriteria.LinearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria(externalId = listOf(harId))
//            // default is UNCONSUMED
//            val results: Vault.Page<HARState> = nodeProvider.services.vaultService.queryBy<HARState>(criteria)
//            val statesInVault: List<StateAndRef<HARState>> = results.states
//
//            //take first state for now
//            val inputState = statesInVault[0]
//
//            assertNotNull(inputState, "Expected state not found in vault")
//            assertEquals("PHP", inputState.state.data.payer, "Payer mis-matched")
//            assertEquals("0002U",inputState.state.data.cptCode, "CPT Code mis-matched")
//            assertEquals("Oncology (colorectal)",inputState.state.data.description, "Epic description mis-matched")
//            assertEquals(HARStatus.OPEN.toString(), inputState.state.data.status, "HARStatus mis-matched")
//        }
//    }
//
//    @Test
//    fun testCheckForAuthRequiredFlowNoAuth () {
//
//        val harId = "10000"
//
//        nodeProvider.startFlow(RecieveFlow( harID = harId,
//                epicRecord = EpicRecord("PHP","Oncology (colorectal)","0002U", scheduledDate = randomDate() )))
//        val future = nodeProvider.startFlow(CheckForAuthRequired(harId))
//        mockNet.runNetwork()
//        val signedTransaction = future.get()
//
//        nodeProvider.transaction {
//            val criteria: QueryCriteria.LinearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria(externalId = listOf(harId))
//            val results: Vault.Page<HARState> = nodeProvider.services.vaultService.queryBy<HARState>(criteria)
//            val statesInVault: List<StateAndRef<HARState>> = results.states
//            val inputState= statesInVault[0]
//
//            assertNotNull(inputState, "Expected state not found in vault")
//            assertEquals("PHP", inputState.state.data.payer, "Payer mis-matched")
//            assertEquals("0002U",inputState.state.data.cptCode, "CPT Code mis-matched")
//            assertEquals("Oncology (colorectal)",inputState.state.data.description, "Epic description mis-matched")
//            assertEquals(HARStatus.CONFIRMED.toString(), inputState.state.data.status, "HARStatus mis-matched")
//        }
//    }
//
//    @Test
//    fun testCheckForAuthRequiredFlowAuthRequired() {
//
//        val harId = "10000"
//
//        nodeProvider.startFlow(RecieveFlow( harID = harId,
//                epicRecord = EpicRecord("PNP","Oncology (colorectal)","0002U", scheduledDate = randomDate() )))
//        val future = nodeProvider.startFlow(CheckForAuthRequired(harId))
//        mockNet.runNetwork()
//        val signedTransaction = future.get()
//
//        nodeProvider.transaction {
//            val criteria: QueryCriteria.LinearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria(externalId = listOf(harId))
//            val results: Vault.Page<HARState> = nodeProvider.services.vaultService.queryBy<HARState>(criteria)
//            val statesInVault: List<StateAndRef<HARState>> = results.states
//            val inputState= statesInVault[0]
//
//            assertNotNull(inputState, "Expected state not found in vault")
//            assertEquals("PNP", inputState.state.data.payer, "Payer mis-matched")
//            assertEquals("0002U",inputState.state.data.cptCode, "CPT Code mis-matched")
//            assertEquals("Oncology (colorectal)",inputState.state.data.description, "Epic description mis-matched")
//            assertEquals(HARStatus.PENDING.toString(), inputState.state.data.status, "HARStatus mis-matched")
//        }
//    }
//
//    @InitiatedBy(GetSignedTransaction::class)
//    class DummyAcceptor(val session: FlowSession) :FlowLogic<Unit>() {
//
//
//        @Suspendable
//        override fun call() {
//            val ftx = session.receive<SignedTransaction>().unwrap { it }
//
//            val payerNode = (serviceHub.networkMapCache.getNodeByLegalName(
//                    NodeIdentity.Payer))!!
//
//
//            val key = payerNode.legalIdentities.first().owningKey
//            val sign = serviceHub.createSignature(ftx, key)
//            val signed = ftx.withAdditionalSignature(sign)
//
//            return session.send(signed)
//        }
//
//    }
//
//    @Test
//    fun testGetApprovalCode() {
//
//        val harId = "10000"
//
//        nodeProvider.startFlow(RecieveFlow( harID = harId,
//                epicRecord = EpicRecord("PNP","Oncology (colorectal)","0002U", scheduledDate = randomDate() )))
//        nodeProvider.startFlow(CheckForAuthRequired(harId))
//        mockNet.runNetwork()
//        nodeProvider.startFlow(GetApprovalCode(harId))
//
//        mockNet.runNetwork()
//
//        nodeProvider.transaction {
//            val criteria: QueryCriteria.LinearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria(externalId = listOf(harId))
//            val results: Vault.Page<HARState> = nodeProvider.services.vaultService.queryBy<HARState>(criteria)
//            val statesInVault: List<StateAndRef<HARState>> = results.states
//            val inputState= statesInVault[0]
//
//            assertNotNull(inputState, "Expected state not found in vault")
//            assertEquals("PNP", inputState.state.data.payer, "Payer mis-matched")
//            assertEquals("0002U",inputState.state.data.cptCode, "CPT Code mis-matched")
//            assertEquals("Oncology (colorectal)",inputState.state.data.description, "Epic description mis-matched")
//            assertEquals(HARStatus.DENIED.toString(), inputState.state.data.status, "HARStatus mis-matched")
//        }
//    }
//}