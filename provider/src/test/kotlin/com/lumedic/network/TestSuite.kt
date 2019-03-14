//package com.lumedic.network
//
//import com.lumedic.network.base.flow.QueryAuthRequired
//import com.lumedic.network.node.NodeIdentity
//import net.corda.core.identity.Party
//import net.corda.core.utilities.loggerFor
//import net.corda.testing.node.MockNetwork
//import net.corda.testing.node.StartedMockNode
//import org.junit.After
//import org.junit.Before
//import org.slf4j.Logger
//import java.time.Instant
//
//abstract class TestSuite {
//    protected lateinit var network: MockNetwork
//    protected lateinit var provider: StartedMockNode
//    protected lateinit var oracle: StartedMockNode
//    protected lateinit var payer: StartedMockNode
//
//    @Before
//    fun setupNetwork() {
//        network = MockNetwork(listOf(
//                "com.lumedic.network.base",
//                "com.lumedic.network.provider",
//                "com.lumedic.network.payer",
//                "com.lumedic.network.oracle"
//        ), threadPerNode = true)
//        provider = network.createPartyNode(NodeIdentity.Provider)
//        oracle = network.createPartyNode(NodeIdentity.Oracle)
//        payer = network.createPartyNode(NodeIdentity.Payer)
//
//        //registerFlowsAndServices()
//    }
//
//    @After
//    fun tearDownNetwork() {
//        network.stopNodes()
//    }
//
//    companion object {
//        val logger: Logger = loggerFor<TestSuite>()
//    }
//
//    private fun calculateDeadlineInSeconds(interval: Long) = Instant.now().plusSeconds(interval)
//    protected val fiveSecondsFromNow get() = calculateDeadlineInSeconds(5L)
//
//    fun StartedMockNode.legalIdentity(): Party {
//        return this.info.legalIdentities.first()
//    }
//
//    protected fun registerFlowsAndServices() {
//        oracle.registerInitiatedFlow(QueryAuthRequired::class.java)
//        payer.registerInitiatedFlow(QueryAuthRequired::class.java)
//    }
//}