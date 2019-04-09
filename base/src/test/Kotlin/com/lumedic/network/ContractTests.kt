package com.lumedic.network

import com.lumedic.network.base.contract.HARSTATE_CONTRACT_ID
import com.lumedic.network.base.contract.HARStateContract
import com.lumedic.network.base.model.HARStatus
import com.lumedic.network.base.state.HARState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import net.corda.testing.node.makeTestIdentityService
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class ContractTests {
    private companion object {
        val dummyProvider = TestIdentity(CordaX500Name("Base Dummy Party1", "London","GB"))
        val dummyPayer = TestIdentity(CordaX500Name("Base Dummy Party2", "London","GB"))
        val dummyParty = TestIdentity(CordaX500Name("Base Dummy Party3", "London","GB"))
        val dummyNotary = TestIdentity(CordaX500Name("Base Dummy Notary", "London","GB"))

        val TEST_TX_TIME: Instant = Instant.parse("2019-04-17T12:00:00.00Z")

        fun dummyEventDate(): Date {
            val ldt = LocalDateTime.now().plusDays(1)
            val zdt = ZonedDateTime.of(ldt, ZoneId.systemDefault())
            val cal = GregorianCalendar.from(zdt)
            return cal.time
        }
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private fun partyKeys(vararg identities: TestIdentity) = identities.map { it.party.owningKey }
    private fun partyIdentities(vararg identities: TestIdentity) = identities.map { it.identity }
    private fun partys(vararg identities: TestIdentity) = identities.map { it.party }

    private val ledgerServices = MockServices(
            // A list of packages to scan for cordapps
            cordappPackages = listOf("com.lumedic.network.base"),

            // The identity represented by this set of mock services. Defaults to a test identity.
            // You can also use the alternative parameter initialIdentityName which accepts a
            // [CordaX500Name]

            initialIdentity = dummyProvider,
            identityService = makeTestIdentityService(dummyProvider.identity, dummyPayer.identity, dummyNotary.identity)
    )

    @Test
    fun `Contract verify state of ReceiveHARData command`() {

        this.ledgerServices.ledger(dummyNotary.party) {

            // NoInputState
            transaction {
                val state = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                input(HARSTATE_CONTRACT_ID, state)
                command(dummyProvider.publicKey, HARStateContract.ReceiveHARData())
                `fails with`("No initial inputs")
            }

            // OneOutputOpenState
            transaction {
                val state = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT","", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                output(HARSTATE_CONTRACT_ID, state)
                command(dummyProvider.publicKey, HARStateContract.ReceiveHARData())
                verifies()
            }

            //MultipleOutputOtherState
            transaction {
                val firstOutputState = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val secondOutputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                output(HARSTATE_CONTRACT_ID, firstOutputState)
                output(HARSTATE_CONTRACT_ID, secondOutputState)
                command(dummyProvider.publicKey, HARStateContract.ReceiveHARData())
                `fails with`("One output")
            }

            // OneOutputOtherState
            transaction {
                val state = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                output(HARSTATE_CONTRACT_ID, state)
                command(dummyProvider.publicKey, HARStateContract.ReceiveHARData())
                `fails with`("Output state should be Open")
            }

            //OutputNoDescription
            transaction {
                val state = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                output(HARSTATE_CONTRACT_ID, state)
                command(dummyProvider.publicKey, HARStateContract.ReceiveHARData())
                `fails with`("Event description should not be empty")
            }

            //OutputCommandSignersCount
            transaction {
                val state = HARState(HARStatus.OPEN.toString(), listOf(), "Dummy description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                output(HARSTATE_CONTRACT_ID, state)
                command(dummyProvider.publicKey, HARStateContract.ReceiveHARData())
                `fails with`("Check number of participants sign required")
            }

            //OutputCommandSigners
            transaction {
                val state = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                output(HARSTATE_CONTRACT_ID, state)
                command(dummyPayer.publicKey, HARStateContract.ReceiveHARData())
                `fails with`("All signers parties must sign")
            }
        }
    }

    @Test
    fun `Contract verify state of CheckAuth command`() {

        this.ledgerServices.ledger(dummyNotary.party) {

            // InputStateCount
            transaction {
                val inputState = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val outputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val cmd = HARStateContract.CheckAuth("123","PHP",true)

                input(HARSTATE_CONTRACT_ID, inputState)
                input(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, cmd)
                `fails with`("One input")
            }

            // OutputStateCount
            transaction {
                val inputState = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val outputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val cmd = HARStateContract.CheckAuth("123","PHP",true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, cmd)
                `fails with`("One output")
            }

            // InputStateOpen
            transaction {
                val inputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val outputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val cmd = HARStateContract.CheckAuth("123","PHP",true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, cmd)
                `fails with`("Input state should be Open")
            }

            // OutputStatePendingIsAuthRequiredFalse
            transaction {
                val inputState = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val outputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(), isAuthRequired = false)
                val cmd = HARStateContract.CheckAuth("123","PHP",true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, cmd)
                `fails with`("Output state should set isAuthRequired to true")
            }

            // OutputStateConfirmedIsAuthRequiredTrue
            transaction {
                val inputState = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val outputState = HARState(HARStatus.CONFIRMED.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(), isAuthRequired = true)
                val cmd = HARStateContract.CheckAuth("123","PHP",true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, cmd)
                `fails with`("Output state should set isAuthRequired to false")
            }

            // OutputStatePendingIsAuthRequiredTrue
            transaction {
                val inputState = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val outputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(), isAuthRequired = true)
                val cmd = HARStateContract.CheckAuth("123","PHP",true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, cmd)
                verifies()
            }

            // OutputStateConfirmedIsAuthRequiredFalse
            transaction {
                val inputState = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val outputState = HARState(HARStatus.CONFIRMED.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(), isAuthRequired = false)
                val cmd = HARStateContract.CheckAuth("123","PHP",true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, cmd)
                verifies()
            }

            // OutputNumOfSigners
            transaction {
                val inputState = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val outputState = HARState(HARStatus.CONFIRMED.toString(), listOf(dummyProvider.party, dummyPayer.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(), isAuthRequired = false)
                val cmd = HARStateContract.CheckAuth("123","PHP",true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, cmd)
                `fails with`("Check number of participants sign required")
            }

            // OutputAllSigners
            transaction {
                val inputState = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val outputState = HARState(HARStatus.CONFIRMED.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(), isAuthRequired = false)
                val cmd = HARStateContract.CheckAuth("123","PHP",true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyPayer.publicKey, cmd)
                `fails with`("All signers parties must sign")
            }
        }
    }

    @Test
    fun `Contract verify state of MarkDeniedConfirmed command`() {

        this.ledgerServices.ledger(dummyNotary.party) {
            // InputStateCount
            transaction {
                val inputState = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val outputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())

                input(HARSTATE_CONTRACT_ID, inputState)
                input(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, HARStateContract.MarkDeniedConfirmed())
                `fails with`("One input")
            }

            // OutputStateCount
            transaction {
                val inputState = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())
                val outputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate())

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, HARStateContract.MarkDeniedConfirmed())
                `fails with`("One output")
            }

            //InputTrueOutputFalseIsAuthRequired
            transaction {
                val inputState = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)
                val outputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = false)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, HARStateContract.MarkDeniedConfirmed())
                `fails with`("Input & output state's isAuthRequired should not be changed")
            }

            //InputFalseOutputTrueIsAuthRequired
            transaction {
                val inputState = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = false)
                val outputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, HARStateContract.MarkDeniedConfirmed())
                `fails with`("Input & output state's isAuthRequired should not be changed")
            }

            //InputStateOpen
            transaction {
                val inputState = HARState(HARStatus.OPEN.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)
                val outputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, HARStateContract.MarkDeniedConfirmed())
                `fails with`("Input state should not be OPEN")
            }

            //InputStatePendingIsAuthFalse
            transaction {
                val inputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = false)
                val outputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = false)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, HARStateContract.MarkDeniedConfirmed())
                `fails with`("Input state should set isAuthRequired to true")
            }

            //OutputStateDeniedIsAuthFalse
            transaction {
                val inputState = HARState(HARStatus.CONFIRMED.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = false)
                val outputState = HARState(HARStatus.DENIED.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = false)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, HARStateContract.MarkDeniedConfirmed())
                `fails with`("Output state should set isAuthRequired to true")
            }

            //OutputStateDeniedAuthCodeEmpty
            transaction {
                val inputState = HARState(HARStatus.CONFIRMED.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)
                val outputState = HARState(HARStatus.DENIED.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, HARStateContract.MarkDeniedConfirmed())
                `fails with`("Output state should set authCode to NA")
            }

            //OutputStateConfirmedAuthCodeEmpty
            transaction {
                val inputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)
                val outputState = HARState(HARStatus.CONFIRMED.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, HARStateContract.MarkDeniedConfirmed())
                `fails with`("Output state should set valid authCode")
            }

            //OutputStateConfirmedAuthCodeNA
            transaction {
                val inputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(), isAuthRequired = true)
                val outputState = HARState(HARStatus.CONFIRMED.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "NA", UniqueIdentifier("123"), dummyEventDate(), isAuthRequired = true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, HARStateContract.MarkDeniedConfirmed())
                `fails with`("Output state should set valid authCode")
            }

            //OutputStateNumOfSigners
            transaction {
                val inputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)
                val outputState = HARState(HARStatus.CONFIRMED.toString(), listOf(dummyProvider.party, dummyPayer.party), "Dummy Description", "123", "CPT", "", "PHP","", "DummyAuthCode", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(dummyProvider.publicKey, HARStateContract.MarkDeniedConfirmed())
                `fails with`("Check number of participants sign required")
            }

            //OutputStateAllSigners
            transaction {
                val inputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)
                val outputState = HARState(HARStatus.CONFIRMED.toString(), listOf(dummyProvider.party, dummyPayer.party), "Dummy Description", "123", "CPT", "", "PHP","", "DummyAuthCode", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(listOf(dummyParty.publicKey, dummyProvider.publicKey), HARStateContract.MarkDeniedConfirmed())

                `fails with`("All signers parties must sign")
            }

            //OutputConfirmed
            transaction {
                val inputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)
                val outputState = HARState(HARStatus.CONFIRMED.toString(), listOf(dummyProvider.party, dummyPayer.party), "Dummy Description", "123", "CPT", "", "PHP","", "DummyAuthCode", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(listOf(dummyProvider.publicKey,dummyPayer.publicKey), HARStateContract.MarkDeniedConfirmed())
                verifies()
            }

            //OutputDenied
            transaction {
                val inputState = HARState(HARStatus.PENDING.toString(), listOf(dummyProvider.party), "Dummy Description", "123", "CPT", "", "PHP","", "", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)
                val outputState = HARState(HARStatus.DENIED.toString(), listOf(dummyProvider.party, dummyPayer.party), "Dummy Description", "123", "CPT", "", "PHP","", "NA", UniqueIdentifier("123"), dummyEventDate(),isAuthRequired = true)

                input(HARSTATE_CONTRACT_ID, inputState)
                output(HARSTATE_CONTRACT_ID, outputState)
                command(listOf(dummyProvider.publicKey,dummyPayer.publicKey), HARStateContract.MarkDeniedConfirmed())
                verifies()
            }
        }
    }
}