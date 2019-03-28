//package com.lumedic.network.oracle.checkauth
//
//import com.lumedic.network.base.contract.HARSTATE_CONTRACT_ID
//import com.lumedic.network.base.contract.HARStateContract
//import com.lumedic.network.base.flow.GetSignedTransaction
//import com.lumedic.network.base.flow.QueryAuthRequired
//import com.lumedic.network.base.flow.SignFilteredTransaction
//import com.lumedic.network.base.model.HARStatus
//import com.lumedic.network.base.model.PayerCTP
//import com.lumedic.network.base.state.HARState
//import com.lumedic.network.oracle.checkauth.flow.QueryHandler
//import net.corda.core.contracts.*
//import net.corda.core.crypto.SecureHash
//import net.corda.core.identity.CordaX500Name
//import net.corda.core.identity.Party
//import net.corda.core.transactions.TransactionBuilder
//import net.corda.testing.node.MockNetwork
//import net.corda.testing.node.StartedMockNode
//import org.junit.After
//import org.junit.Before
//import org.junit.Test
//import java.time.Instant
//import java.time.LocalDateTime
//import java.time.ZoneId
//import java.time.ZonedDateTime
//import java.util.*
//import java.util.function.Predicate
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
//        mockNet = MockNetwork(listOf("com.lumedic.network.base", "com.lumedic.network.payer", "com.lumedic.network"), threadPerNode = false)
//        nodePayer = mockNet.createPartyNode(CordaX500Name("Payer", "London", "GB"))
//        nodeOracle = mockNet.createPartyNode(CordaX500Name("Oracle", "New York", "US"))
//        nodeNotary = mockNet.createPartyNode(CordaX500Name("Notary", "London", "GB"))
//        nodeProvider = mockNet.createPartyNode(CordaX500Name("Provider", "London", "GB"))
//        registerFlowsAndServices(nodeOracle)
//    }
//
//    fun registerFlowsAndServices(node: StartedMockNode) {
//        node.registerInitiatedFlow(QueryHandler::class.java)
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
//    @Test
//    fun testQueryHandlerAuthRequiredForPayerPHP() {
//
//        val flow = QueryAuthRequired(nodeOracle.legalIdentity(), PayerCTP("0002U", "PHP"))
//        val future = nodePayer.startFlow(flow)
//        mockNet.runNetwork()
//        val payerCTP = future.get();
//        assert (payerCTP.isAuthRequired == true)
//        assert (payerCTP.ctpCode == "0002U")
//        assert (payerCTP.payer == "PHP")
//    }
//
//    @Test
//    fun testQueryHandlerAuthRequiredForPayerPHPInvalidCPTCode() {
//
//        val flow = QueryAuthRequired(nodeOracle.legalIdentity(), PayerCTP("invalid", "PHP"))
//        val future = nodePayer.startFlow(flow)
//        mockNet.runNetwork()
//        val payerCTP = future.get();
//        assert (payerCTP.isAuthRequired == false)
//    }
//
//    @Test
//    fun testQueryHandlerAuthRequiredForNonPHPPayers() {
//
//        val flow = QueryAuthRequired(nodeOracle.legalIdentity(), PayerCTP("0002A", "Aetna"))
//        val future = nodePayer.startFlow(flow)
//        mockNet.runNetwork()
//        val payerCTP = future.get();
//        assert (payerCTP.isAuthRequired == true)
//        assert (payerCTP.ctpCode == "0002A")
//        assert (payerCTP.payer == "Aetna")
//    }
//
//    @Test
//    fun testQueryHandlerAuthRequiredForNonPHPPayersInvalidCPTCode() {
//        val flow = QueryAuthRequired(nodeOracle.legalIdentity(), PayerCTP("invalid", "Aetna"))
//        val future = nodePayer.startFlow(flow)
//        mockNet.runNetwork()
//        val payerCTP = future.get();
//        assert (payerCTP.isAuthRequired == false)
//    }
//
//    data class EpicRecord(val payer: String, val desc: String, val cptCode: String, var scheduledDate: Date? = null)
//
//    fun LocalDateTime.toDate(): Date {
//        val zdt = ZonedDateTime.of(this, ZoneId.systemDefault())
//        val cal = GregorianCalendar.from(zdt)
//        return cal.time
//    }
//
//    fun randomInt(min: Int, max: Int): Int {
//        if (min > max || max - min + 1 > Int.MAX_VALUE) throw IllegalArgumentException("Invalid Range")
//        return Random().nextInt(max - min + 1) + min
//    }
//
//    fun randomDate(): Date {
//        val ldt = LocalDateTime.now().plusDays(randomInt(0, 30 * 4).toLong())
//        return ldt.toDate()
//    }
//
//    @Test
//    fun testSignHandler() {
//        val harID = "10000"
//        val epicRecord = EpicRecord("PHP", "Oncology (colorectal)", "0002U", scheduledDate = randomDate())
//        val uniqueIdentifier = UniqueIdentifier(harID)
//        val inputState = HARState(HARStatus.OPEN.toString(), listOf(nodeProvider.legalIdentity()),
//                epicRecord.desc, harID, epicRecord.cptCode,"", epicRecord.payer,"", "", uniqueIdentifier,
//                epicRecord.scheduledDate!!, isAuthRequired = true)
//
//        val outputState = HARState(HARStatus.PENDING.toString(), listOf(nodeProvider.legalIdentity(), nodeOracle.legalIdentity()),
//                inputState.description, harID, inputState.cptCode,"", inputState.payer,"", "authCode", uniqueIdentifier,
//                inputState.eventTime, Date.from(Instant.now()), true)
//
//        val txBuilder = TransactionBuilder(notary = nodeNotary.legalIdentity())
//        val outputContractAndState = StateAndContract(outputState, HARSTATE_CONTRACT_ID)
//        val cmd = Command(HARStateContract.CheckAuth(outputState.cptCode, outputState.payer, outputState.isAuthRequired),
//                listOf(nodeOracle.legalIdentity().owningKey, nodeProvider.legalIdentity().owningKey))
//
//        val inputStateRef: StateAndRef<HARState> = StateAndRef(TransactionState(inputState, HARSTATE_CONTRACT_ID, notary = nodeNotary.legalIdentity()),
//                StateRef(SecureHash.zeroHash, 0));
//        txBuilder.addInputState(inputStateRef)
//        // We add the items to the builder.
//        txBuilder.withItems(outputContractAndState, cmd)
//        val ptx = nodeProvider.services.signInitialTransaction(txBuilder)
//
//        val ftx = ptx.buildFilteredTransaction(Predicate {
//            when (it) {
//                is Command<*> -> nodeOracle.legalIdentity().owningKey in it.signers && it.value is HARStateContract.CheckAuth
//                else -> false
//            }
//        })
//        val flow = SignFilteredTransaction(nodeOracle.legalIdentity(), ftx)
//        val future = nodePayer.startFlow(flow)
//        mockNet.runNetwork()
//        val oracleSignature = future.get();
//
//        val oracleKey = nodeOracle.legalIdentity().owningKey
//        val oracleSign = nodeOracle.services.createSignature(ptx, oracleKey)
//        assert (oracleSignature == oracleSign)
//    }
//}