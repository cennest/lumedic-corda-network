package net.corda.server.controllers

import com.lumedic.network.CheckForAuthRequired
import com.lumedic.network.GetApprovalCode
import com.lumedic.network.RecieveFlow
import com.lumedic.network.base.model.EpicRecord
import com.lumedic.network.base.model.HARStatus
import com.lumedic.network.base.schema.HARStateSchemaV1
import com.lumedic.network.base.state.HARState
import net.corda.core.contracts.StateAndRef
import net.corda.core.internal.randomOrNull
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.SignedTransaction
import net.corda.server.NodeRPCConnection
import org.slf4j.LoggerFactory
import java.util.*
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import net.corda.client.jackson.JacksonSupport
import net.corda.core.messaging.*
import net.corda.server.model.api.ConsolidatedRecordCount
import net.corda.server.model.api.HARRecordResults
import net.corda.server.model.api.RecordCount
import net.corda.server.randomInt
import net.corda.server.toDate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime



/**
 * Define CorDapp-specific endpoints in a controller such as this.
 */
@RestController
@RequestMapping("api/records") // The paths for GET and POST requests are relative to this base path.
class CustomController(
        private val rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
        var mapper = JacksonSupport.createNonRpcMapper()

        fun randomDate() : Date {
            val ldt = LocalDateTime.now().plusDays(randomInt(0, 30*4).toLong())
            return ldt.toDate()
        }
    }


    fun fillEpicRecords() {

        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "PHP", branch = "Sweden",      desc = "Oncology (colorectal)", cptCode = "0002U"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "PHP", branch = "Sweden",      desc = "Infectious disease (bacterial)", cptCode = "0004U"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "PHP", branch = "California",  desc = "Oncology (prostrate)", cptCode = "0005U"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "PHP", branch = "California",  desc = "Fetal aneuploidy (trisomy 21, and 18)", cptCode = "0009M"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "PHP", branch = "Sweden",      desc = "Antiprothrombin ", cptCode = "0030T"))

        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "PHP", branch = "Sweden",     desc = "Cerebral perfusion analysis", cptCode = "0042T"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "PHP", branch = "Sweden",     desc = "Oncology (prostate)", cptCode = "0047U"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "PHP", branch = "California", desc = "Replacement/repair of thoracic unit", cptCode = "0052T"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "PHP", branch = "California", desc = "Cryopreservation", cptCode = "0058T"))

        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "Aetna", branch = "Sweden", desc = "HDR Electronic Brachytherhapy Per Fraction", cptCode = "0002A"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "Aetna", branch = "Sweden", desc = "Immature oocyte(s)", cptCode = "0004A"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "Aetna", branch = "California", desc = "Infectious disease (bacterial)", cptCode = "0006A"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "Aetna", branch = "California", desc = "Oncology (hematolymphoid neoplasia)", cptCode = "0009T"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "Aetna", branch = "Sweden", desc = "EGFR (Epidermal growth factor receptor) ", cptCode = "003GT"))

        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "Aetna", branch = "Sweden", desc = "Stereotactic", cptCode = "004FT"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "Aetna", branch = "Sweden", desc = "HDR Electronic Brachytherhapy", cptCode = "006YU"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "Aetna", branch = "California", desc = "Bioimpedance spectroscopy (BIS)", cptCode = "0130I"))

        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "UHC", branch = "Sweden", desc = "Bronchoscopy", cptCode = "00987"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "UHC", branch = "Sweden", desc = "Percutaneous transcatheter", cptCode = "00467"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "UHC", branch = "California", desc = "Myocardial sympathetic innervation imaging", cptCode = "00432"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "UHC", branch = "California", desc = "Ablation", cptCode = "00456"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "UHC", branch = "Sweden", desc = "Pre-Sacral Interbody Technique ", cptCode = "00356"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "UHC", branch = "Sweden", desc = "L4-L5 Interspace", cptCode = "00345"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "UHC", branch = "Sweden", desc = "Ligation", cptCode = "00645"))
        epicRecordList.add(EpicRecord(provider = "PSJH", payer = "UHC", branch = "California", desc = "Pharmacogenetic Testing", cptCode = "01334"))
    }
    private val epicRecordList= mutableListOf<EpicRecord>()
    private val proxy = rpc.proxy

    @GetMapping(value = "/customendpoint", produces = arrayOf("text/plain"))
    private fun status() = "Modify this."

    private fun getMaxHarIDInSystem():Int{


        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
        val pageSpec= PageSpecification(1, 1)
        val results= proxy.vaultQueryBy<HARState>(criteria = generalCriteria,paging=pageSpec).totalStatesAvailable
        // return if(results.count()==0)0 else  results.last().state.data.harID.toInt()
        //return if(results.otherResults[0] ==null)0 else results.otherResults[0] as Int
        return results.toInt()
    }

    @CrossOrigin
    @GetMapping(value="/getdata", produces= arrayOf("application/json"))
    fun getDummyDataFromEPIC(): Response {
        if(epicRecordList.count()==0) {
            fillEpicRecords()
        }
        val maxHarID=  getMaxHarIDInSystem()
        val payersList= listOf("Aetna","UHC","PHP")
        val descriptions= listOf("Oncology (breast cancer)","Oncology (hematolymphoid neoplasia)","Targeted genomic sequence analysis","Cerebral perfusion analysis","Oncology (breast ductal carcinoma in situ)","Oncology (prostate)")

        var tokenCount=maxHarID
        while(tokenCount<maxHarID+100)
        {
            tokenCount= tokenCount+1
            var epicRecord= epicRecordList.randomOrNull()
            if (epicRecord==null)
            {
                epicRecord= EpicRecord(provider = "PSJH", payer = "PHP", branch = "Sweden",desc = "Oncology (colorectal)", cptCode = "0002U")
            }
            /* var payerName= payersList.randomOrNull()
             if(payerName==null)
             {
                 payerName="Aetna"
             }
             var desc= descriptions.randomOrNull()
             if(desc==null)
             {
                 desc="Radiology"
             }
             var cptCode= desc.take(3)+payerName.take(2)*/
            epicRecord.scheduledDate = randomDate()
            val track=   rpc.proxy.startFlow(::RecieveFlow,tokenCount.toString(), epicRecord)

        }
        return Response.ok().build()
    }

    @CrossOrigin
    @GetMapping(value="/records/{page}/{size}", produces= arrayOf("application/json"))
    fun getRecordsInVault(@PathVariable("page") page: Int, @PathVariable("size")size:Int): ResponseEntity<HARRecordResults> {

        // val start =  LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).minus(2,ChronoUnit.DAYS)
        // val end = start.plus(30, ChronoUnit.DAYS)
        // val recordedBetweenExpression = QueryCriteria.TimeCondition(
        //  QueryCriteria.TimeInstantType.RECORDED,
        //  ColumnPredicate.Between(start, end))

        val pageSpec= PageSpecification(page, size)
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        var result= proxy.vaultQueryBy<HARState>(criteria= generalCriteria,paging=pageSpec)
        var recordsInMyVault = result.states
        val totalRecords= result.totalStatesAvailable
        val consolidatedResponse= HARRecordResults(totalRecords , recordsInMyVault)
        return ResponseEntity(consolidatedResponse, HttpStatus.OK)
    }

    @CrossOrigin
    @GetMapping(value="/countsbystatus")
    fun getRecordCountsByStatus(): ResponseEntity<ConsolidatedRecordCount>
    {
        val openRecordCount= RecordCount(HARStatus.OPEN.toString(),getCountByStatus(HARStatus.OPEN.toString()))
        val pendingRecordCount= RecordCount(HARStatus.PENDING.toString(),getCountByStatus(HARStatus.PENDING.toString()))

        val confirmedRecordCount= RecordCount(HARStatus.CONFIRMED.toString(),getCountByStatus(HARStatus.CONFIRMED.toString()))

        val deniedRecordCount= RecordCount(HARStatus.DENIED.toString(),getCountByStatus(HARStatus.DENIED.toString()))
        val consolidatedResult= ConsolidatedRecordCount(listOf(openRecordCount,pendingRecordCount,confirmedRecordCount,deniedRecordCount))
        return ResponseEntity<ConsolidatedRecordCount>(consolidatedResult, HttpStatus.OK)

    }

    @CrossOrigin
    @GetMapping(value="/countbystatus/{status}", produces= arrayOf("application/json"))
    fun getRecordCountByStatus(@PathVariable("status") status:String): ResponseEntity<RecordCount>
    {
        val recordCount= RecordCount(status,getCountByStatus(status))
        return ResponseEntity(recordCount, HttpStatus.OK)

    }
    private fun getCountByStatus(status :String):Long
    {
        //totalStatesAvailable returns -1 unless a page spec is given so lets give it.
        val pageSpec= PageSpecification(1, 10)
        var recordsByStatus = HARStateSchemaV1.PersistentHARState::status.equal(status)
        val customCriteria = QueryCriteria.VaultCustomQueryCriteria(recordsByStatus, status = Vault.StateStatus.UNCONSUMED)
        val result = proxy.vaultQueryBy<HARState>(customCriteria,paging=pageSpec)
        val totalRecords= result.totalStatesAvailable
        return totalRecords
    }

    @CrossOrigin
    @GetMapping(value="/recordUpdates/{harIDs}", produces= arrayOf("application/json"))
    fun getLatestStatus(@PathVariable("harIDs")harIDs:String):ResponseEntity<List<HARState>>
    {
        val idList= harIDs.split(',')
        val recordsList = mutableListOf<HARState>()
        for (harID in idList) {
            val criteria: QueryCriteria.LinearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria(externalId = listOf(harID))

            // default is UNCONSUMED
            val results: Vault.Page<HARState> = proxy.vaultQueryBy<HARState>(criteria)
            val statesInVault: List<StateAndRef<HARState>> = results.states

            //take first state for now
            val inputState = statesInVault[0]
            recordsList.add(inputState.state.data)
        }
        return ResponseEntity(recordsList,HttpStatus.OK)

    }

    @CrossOrigin
    @GetMapping(value="/recordsbystatus/{status}/{page}/{size}", produces= arrayOf("application/json"))
    fun getRecordsInVaultByStatus(@PathVariable("status") status: String, @PathVariable("page") page: Int, @PathVariable("size")size:Int): ResponseEntity<HARRecordResults> {
        // val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val pageSpec= PageSpecification(page, size)
        val results = builder {
            var recordsByStatus = HARStateSchemaV1.PersistentHARState::status.equal(status)
            val customCriteria = QueryCriteria.VaultCustomQueryCriteria(recordsByStatus, status = Vault.StateStatus.UNCONSUMED)
            val result = proxy.vaultQueryBy<HARState>(customCriteria, paging=pageSpec)
            var recordsInMyVault = result.states
            val totalRecords= result.totalStatesAvailable
            val consolidatedResponse= HARRecordResults(totalRecords , recordsInMyVault)
            return ResponseEntity(consolidatedResponse,HttpStatus.OK)
        }
    }

    @CrossOrigin
    @GetMapping(value="/getAuthCode", produces= arrayOf("application/json"))
    fun getAuthorizationCode(): Response {
        //  val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
        // val results = builder {
        var harByStatus = HARStateSchemaV1.PersistentHARState::status.equal(HARStatus.PENDING.toString())
        val customCriteria = QueryCriteria.VaultCustomQueryCriteria(harByStatus,status = Vault.StateStatus.UNCONSUMED)
        // val criteria = generalCriteria.and(customCriteria)
        val results = proxy.vaultQueryBy<HARState>(customCriteria).states
        for(r in results)
        {
            proxy.startFlow(::GetApprovalCode,r.state.data.harID)
        }
        //}
        return Response.ok().build()
    }

    @CrossOrigin
    @GetMapping(value="/checkauth", produces= arrayOf("application/json"))
    fun checkForAuthorization(): Response {
        //  val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
        //  val results = builder {
        var harByStatus = HARStateSchemaV1.PersistentHARState::status.equal("OPEN")
        val customCriteria = QueryCriteria.VaultCustomQueryCriteria(harByStatus,status = Vault.StateStatus.UNCONSUMED)
        // val criteria = generalCriteria.and(customCriteria)
        val results = proxy.vaultQueryBy<HARState>(customCriteria).states
        for(r in results)
        {
            proxy.startFlow(::CheckForAuthRequired,r.state.data.harID)
        }
        //}
        return Response.ok().build()
    }

    @CrossOrigin
    @GetMapping(value="/gethistory/{id}")
    fun getAllStatesOfaClaim(@PathVariable("id")id:String):ResponseEntity<List<StateAndRef<HARState>>>{
        // val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.CONSUMED)
        val linearCriteria: QueryCriteria.LinearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria(externalId = listOf(id),status = Vault.StateStatus.ALL)
        // val criteria:QueryCriteria= generalCriteria.and(linearCriteria)

        val results: Vault.Page<HARState> = proxy.vaultQueryBy<HARState>(linearCriteria)
        val statesInVault: List<StateAndRef<HARState>> = results.states
        return ResponseEntity(statesInVault, HttpStatus.OK)
    }
}