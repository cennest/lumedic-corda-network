package com.lumedic.network.braid

import com.lumedic.network.CheckForAuthRequired
import com.lumedic.network.GetApprovalCode
import com.lumedic.network.RecieveFlow
import com.lumedic.network.base.model.EpicRecord
import com.lumedic.network.base.model.HARStatus
import com.lumedic.network.base.schema.HARStateSchemaV1
import com.lumedic.network.base.state.HARState
import com.lumedic.network.db.ProviderDbService
import com.lumedic.network.entity.AnalysisReport
import com.lumedic.network.entity.ReportType
import com.lumedic.network.entity.SimulatorStateSubscriber
import io.bluebank.braid.corda.services.transaction
import io.bluebank.braid.core.async.all
import io.bluebank.braid.core.async.catch
import io.bluebank.braid.core.async.mapUnit
import io.bluebank.braid.core.async.onSuccess
import io.vertx.core.Future
import io.vertx.core.Vertx
import net.corda.client.jackson.JacksonSupport
import net.corda.core.contracts.StateAndRef
import net.corda.core.internal.randomOrNull
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.MAX_PAGE_SIZE
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.contextDatabase
import net.corda.nodeapi.internal.persistence.wrapWithDatabaseTransaction
import org.slf4j.Logger
import rx.Observable
import rx.Subscription
import rx.observables.SyncOnSubscribe
import rx.schedulers.Schedulers
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*


class VaultService(private val serviceHub: AppServiceHub, private val vertx: Vertx) {

    data class HARRecordResults(val totalRecords:Long,val records: List<StateAndRef<HARState>>)
    data class RecordCount(val status: String, val count:Long)
    data class ConsolidatedRecordCount(val recordCounts: List<RecordCount>)

    private val epicRecordList = mutableListOf<EpicRecord>()

    companion object {

        val logger: Logger = loggerFor<VaultService>()
        var mapper = JacksonSupport.createNonRpcMapper()

        fun randomInt(min: Int, max: Int) : Int {
            if(min > max || max - min+1 > Int.MAX_VALUE) throw IllegalArgumentException("Invalid Range")
            return Random().nextInt(max - min + 1) + min
        }

        fun randomDate(min: Int = 0, max: Int = 30*4) : Date {
            val ldt = LocalDateTime.now().plusDays(randomInt(min, max).toLong())
            return ldt.toDate()
        }
        fun randomLocalDateTime(min: Int = 0, max: Int = 30*4) : LocalDateTime {
            val ldt = LocalDateTime.now().plusDays(randomInt(min, max).toLong())
            return ldt
        }

        fun LocalDateTime.addMinutes(min: Int = 0, max: Int = 0) : LocalDateTime {
            val ldt = this.plusMinutes(randomInt(min, max).toLong())
            return ldt
        }

        fun LocalDateTime.toDate() : Date {
            val zdt = ZonedDateTime.of(this, ZoneId.systemDefault())
            val cal = GregorianCalendar.from(zdt)
            return cal.time
        }
    }

    private fun fillEpicRecords() {


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

    private fun getMaxHarIDInSystem():Int{
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
        val pageSpec= PageSpecification(1, 1)
        val results= serviceHub.vaultService.queryBy<HARState>(criteria = generalCriteria,paging=pageSpec).totalStatesAvailable
        // return if(results.count()==0)0 else  results.last().state.data.harID.toInt()
        //return if(results.otherResults[0] ==null)0 else results.otherResults[0] as Int
        return results.toInt()
    }

    fun getDummyDataFromEPIC(): Future<Unit> {

        if(epicRecordList.count()==0) {
            fillEpicRecords()
        }
        val maxHarID=  getMaxHarIDInSystem()

        var tokenCount=maxHarID

        return (tokenCount..tokenCount+100).map { id ->

            var epicRecord = epicRecordList.randomOrNull()
                                ?: EpicRecord(provider = "PSJH", payer = "PHP", branch = "Sweden",desc = "Oncology (colorectal)", cptCode = "0002U")

            epicRecord.scheduledDate = randomDate()
            val harId = (id+1).toString()
            val epic = epicRecord!!

            logger.info("HarID: $harId")
            serviceHub.startFlow(RecieveFlow(harId, epic)).returnValue.toVertxFuture()

        }.all().onSuccess {
                    logger.info("generateHars : Finished")
                }
                .catch { err ->
                    logger.error("failed to create Hars", err)
                }
                .mapUnit() // map to a Future<Unit>


    }

    fun getAuthorizationCode(): Boolean {

        val pageSpec= PageSpecification(1, MAX_PAGE_SIZE)

        var harByStatus = HARStateSchemaV1.PersistentHARState::status.equal(HARStatus.PENDING.toString())
        val customCriteria = QueryCriteria.VaultCustomQueryCriteria(harByStatus,status = Vault.StateStatus.UNCONSUMED)
        // val criteria = generalCriteria.and(customCriteria)
        val results = serviceHub.vaultService.queryBy<HARState>(customCriteria, pageSpec).states
        return results.map { st ->
            serviceHub.startFlow(GetApprovalCode(st.state.data.harID)).returnValue.toVertxFuture()
        }.all().succeeded()
    }

    fun checkForAuthorization(): Boolean {

        val pageSpec= PageSpecification(1, MAX_PAGE_SIZE)

        var harByStatus = HARStateSchemaV1.PersistentHARState::status.equal("OPEN")
        val customCriteria = QueryCriteria.VaultCustomQueryCriteria(harByStatus,status = Vault.StateStatus.UNCONSUMED)
        // val criteria = generalCriteria.and(customCriteria)
        val results = serviceHub.vaultService.queryBy<HARState>(customCriteria, pageSpec).states

        return results.map { st ->
            serviceHub.startFlow(CheckForAuthRequired(st.state.data.harID)).returnValue.toVertxFuture()
        }.all().succeeded()
    }

    fun getRecordsInVaultByStatus( status: String, page: Int, size:Int): HARRecordResults {
        // val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val pageSpec= PageSpecification(page, size)
        val results = builder {
            var recordsByStatus = HARStateSchemaV1.PersistentHARState::status.equal(status)
            val customCriteria = QueryCriteria.VaultCustomQueryCriteria(recordsByStatus, status = Vault.StateStatus.UNCONSUMED)
            val result = serviceHub.vaultService.queryBy<HARState>(customCriteria, paging=pageSpec)
            var recordsInMyVault = result.states
            val totalRecords= result.totalStatesAvailable
            val consolidatedResponse= HARRecordResults(totalRecords , recordsInMyVault)
            return consolidatedResponse
        }
    }

    fun getRecordsInVault(page: Int, size: Int): HARRecordResults {
        val pageSpec= PageSpecification(page, size)
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        var result= serviceHub.vaultService.queryBy<HARState>(criteria= generalCriteria,paging=pageSpec)
        var recordsInMyVault = result.states
        val totalRecords= result.totalStatesAvailable
        val consolidatedResponse= HARRecordResults(totalRecords , recordsInMyVault)
        return consolidatedResponse
    }

    fun getRecordCountByStatus(status:String): RecordCount
    {
        val recordCount= RecordCount(status,getCountByStatus(status))
        return recordCount

    }
    private fun getCountByStatus(status :String):Long
    {
        //totalStatesAvailable returns -1 unless a page spec is given so lets give it.
        val pageSpec= PageSpecification(1, 10)
        var recordsByStatus = HARStateSchemaV1.PersistentHARState::status.equal(status)
        val customCriteria = QueryCriteria.VaultCustomQueryCriteria(recordsByStatus, status = Vault.StateStatus.UNCONSUMED)
        val result = serviceHub.vaultService.queryBy<HARState>(customCriteria,paging=pageSpec)
        val totalRecords= result.totalStatesAvailable
        return totalRecords
    }

    fun getRecordCountsByStatus(): ConsolidatedRecordCount
    {
        val dbService = serviceHub.cordaService(ProviderDbService::class.java)
        val statusCount = dbService.getHarStatusCount()
        return statusCount!!



        //subscribe
        val openRecordCount= RecordCount(HARStatus.OPEN.toString(),getCountByStatus(HARStatus.OPEN.toString()))
        val pendingRecordCount= RecordCount(HARStatus.PENDING.toString(),getCountByStatus(HARStatus.PENDING.toString()))

        val confirmedRecordCount= RecordCount(HARStatus.CONFIRMED.toString(),getCountByStatus(HARStatus.CONFIRMED.toString()))

        val deniedRecordCount= RecordCount(HARStatus.DENIED.toString(),getCountByStatus(HARStatus.DENIED.toString()))
        val consolidatedResult= ConsolidatedRecordCount(listOf(openRecordCount,pendingRecordCount,confirmedRecordCount,deniedRecordCount))
        return consolidatedResult

    }

    fun getOverAllStatus():Observable<ConsolidatedRecordCount> {
        return Observable.create { subscriber ->

                serviceHub.transaction {  }

                val sub1 = object : IndependentSubscriber(vertx){

                    override fun isActive(): Boolean = !subscriber.isUnsubscribed
                    override fun handleSubscribeEvent() : Subscription {

                        val pageSpec = PageSpecification(1, 10)
                        val criteria: QueryCriteria.LinearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria()
                        val results = serviceHub.vaultService.trackBy(contractStateType = HARState::class.java, criteria = criteria, paging = pageSpec)
                        val updates= results.updates

                        val vaultSub = updates.subscribe {
                            update ->
                            notify(update)
                        }

                        notify(null)
                        return vaultSub
                    }

                    override fun <T> onNotify(rawUpdate : T) {

                        val dbService = serviceHub.cordaService(ProviderDbService::class.java)
                        val statusCount = dbService.getHarStatusCount()
                        subscriber.onNext(statusCount)
                    }
                }
        }
    }

    fun getAllStatesOfaClaim(id:String):List<StateAndRef<HARState>> {
        // val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.CONSUMED)
        val linearCriteria: QueryCriteria.LinearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria(externalId = listOf(id),status = Vault.StateStatus.ALL)
        // val criteria:QueryCriteria= generalCriteria.and(linearCriteria)

        val results: Vault.Page<HARState> = serviceHub.vaultService.queryBy<HARState>(linearCriteria)
        val statesInVault: List<StateAndRef<HARState>> = results.states
        return statesInVault
    }

    fun getLatestStatus(harIDs:String):Observable<List<HARState>>
    {


        return Observable.create { subscriber ->

            val sub1 = object : IndependentSubscriber(vertx){
                override fun isActive(): Boolean = !subscriber.isUnsubscribed
                override fun handleSubscribeEvent() : Subscription {


                    val criteria: QueryCriteria.LinearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria()
                    val results = serviceHub.vaultService.trackBy(contractStateType = HARState::class.java, criteria = criteria)
                    val updates = results.updates

                    // this was the key technique to avoid jamming up corda's hibernate transactions
                    val threadUpdate = updates.observeOn(Schedulers.computation()).wrapWithDatabaseTransaction()
                    return threadUpdate.subscribe {
                        update ->
                        notify(update)
                    }
                }

                override fun <T> onNotify(rawUpdate : T) {
                    val update = rawUpdate as Vault.Update<HARState>
                    val idList= harIDs.split(',')
                    var harDataList :MutableList<HARState> = arrayListOf();
                    update.produced.forEach{ harDataList.add( it.state.data) }
                    val filteredUpdates = harDataList.filter { it -> idList.contains(it.harID) }
                    if(filteredUpdates.count() > 0) {
                        subscriber.onNext(filteredUpdates)
                    }
                }
            }
        }
    }

    fun analyticReport(type : ReportType, provider : String) : Observable<AnalysisReport> {

        /*
        val a = SyncOnSubscribe.createStateless<AnalysisReport> { observer ->

            val sub = object : SimulatorStateSubscriber(vertx, type, provider ){
                override fun onNotify(update: AnalysisReport) {
                    observer.onNext(update)
                }
            }
        }
        return Observable.create(a)
        */

        return Observable.create { subscriber ->

            val reportService = serviceHub.cordaService(ReportService::class.java)

            val sub = object : SimulatorStateSubscriber(vertx, type, provider, reportService){
                override fun isActive(): Boolean = !subscriber.isUnsubscribed
                override fun onNotify(update: AnalysisReport) {
                    subscriber.onNext(update)
                }
            }
        }
    }
}