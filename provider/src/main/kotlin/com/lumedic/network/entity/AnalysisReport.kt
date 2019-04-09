package com.lumedic.network.entity

import com.lumedic.network.base.model.HARStatus
import com.lumedic.network.braid.ReportService
import com.lumedic.network.braid.VaultService
import io.vertx.core.Vertx
import net.corda.nodeapi.internal.persistence.contextDatabase
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors


data class Field(val Name:String, val Value:Double, val Percent: Double, val State : ProcessStateType = ProcessStateType.NoChange)
data class Process(val Order : Int, val Name : String, val Fields: List<Field>, val State : ProcessStateType = ProcessStateType.NoChange)

data class Provider(val Name:String, val Processes : List<Process>)
data class Payer(val Name:String, val Processes : List<Process>)
data class Branch(val Name:String, val Processes : List<Process>)
data class Patient(val harId:String, val Processes : List<Process>)

data class AutomationAnalytic(val ValueAdded : String, val Productivity : String)
data class PreAuthorization(val Items : List<Process>)

enum class ReportType { YTD, MTD}
enum class ProcessType { Manual, Automated}
enum class ProcessStateType { NoChange, Improvement, Worsening }

data class AnalysisReport(
        val Type:ReportType,
        val Provider:Provider ,
        val Payer:List<Payer>,
        val Branch:List<Branch>,
        val Har:List<Patient>,
        val PreAuthorization:PreAuthorization,
        val AutomationAnalytic:AutomationAnalytic)



data class HarRecord(
        val HarID:String,
        val Provider:String,
        val Payer:String,
        val Branch:String,
        val ProcessType : ProcessType,
        var EventDate:LocalDateTime,
        var Status:String,
        var Description:String = "",
        var CPTCode:String = "NA",
        var IsAuthorizationRequired:Boolean = false){

    fun copy() : HarRecord{

        var aHar = HarRecord(
                HarID = this.HarID,
                Provider = this.Provider,
                Payer = this.Payer,
                Branch = this.Branch,
                ProcessType = this.ProcessType,
                EventDate = this.EventDate,
                Status = this.Status,
                Description = this.Description,
                CPTCode = this.CPTCode,
                IsAuthorizationRequired = this.IsAuthorizationRequired)

        return aHar
    }
}


open class SimulatorStateRefresher(val vertx: Vertx){


    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm:ss")
    }

    init {

        // create a timer publisher to the eventbus
        vertx.setPeriodic(4000) {
            vertx.eventBus().publish("SimulatorStateRefresher", timeFormat.format(Date()))
        }
    }
}

open class SimulatorStateSubscriber(vertx: Vertx, type : ReportType, provider : String, reportService: ReportService ) {

    private var reportGenerator : AnalysisReportGenerator = AnalysisReportGenerator()

    companion object {
        val executor: Executor = Executors.newFixedThreadPool(1)!!
    }

    init {
        val consumer = vertx.eventBus().consumer<String>("SimulatorStateRefresher")
        consumer.handler {
            if(!isActive()) {
                consumer.unregister()
            } else {

                executor.execute {
                    val report = contextDatabase.transaction {
                        reportGenerator.dummyManualDataset().map { r ->  reportService.addHarRecord(r) }
                        //reportGenerator.dummyAutomationDataset().map { r ->  reportService.addHarRecord(r) }
                        reportService.fetchReportAnalysis(type, provider)
                    }

                    if(report != null) onNotify(report)
                }
            }
        }
    }

    open fun isActive(): Boolean = true
    open fun onNotify(update : AnalysisReport) = Unit
}


class AnalysisReportGenerator {

    val branches : List<String> = listOf("Sweden", "California")
    val payers : List<String> = listOf("Providence", "Swedish", "St Joseph")
    val providers : List<String> = listOf("PSJH", "Oregen","NoCal","W Wash", "AK")

    fun dummyManualDataset() : List<HarRecord>{

        var hars = mutableListOf<HarRecord>()
        var len = VaultService.randomInt(2,5)
        for(i in 1..len){

            val dt = LocalDateTime.now()

            var har1 = HarRecord(
                    HarID = UUID.randomUUID().toString(),//"${dt.year}-${dt.month.value}-${dt.dayOfMonth}-${dt.hour}-${dt.minute}-${dt.second}-${System.currentTimeMillis()}",
                    Provider = providers[VaultService.randomInt(0,4)],
                    Payer = payers[VaultService.randomInt(0,2)],
                    Branch = branches[VaultService.randomInt(0,1)],
                    ProcessType = ProcessType.Manual,
                    EventDate = VaultService.randomLocalDateTime(0, 0).minusDays(VaultService.randomInt(0,100).toLong()),
                    Status = HARStatus.OPEN.toString())

            var har2 = har1.copy()
            har2.EventDate = har2.EventDate.plusMinutes(VaultService.randomInt(5, 5).toLong())

            har2.Status = HARStatus.PENDING.toString()
            har2.IsAuthorizationRequired = true

            hars.add(har1)
            hars.add(har2)
        }

        return hars
    }

    fun dummyAutomationDataset() : List<HarRecord>{

        var hars = mutableListOf<HarRecord>()
        var len = VaultService.randomInt(2,5)

        for(i in 1..len){

            val dt = LocalDateTime.now()

            var har1 = HarRecord(
                    HarID =  UUID.randomUUID().toString(),// "${dt.year}-${dt.month.value}-${dt.dayOfMonth}-${dt.hour}-${dt.minute}-${dt.second}-${System.currentTimeMillis()}",
                    Provider = providers[VaultService.randomInt(0,4)],
                    Payer = payers[VaultService.randomInt(0,2)],
                    Branch = branches[VaultService.randomInt(0,1)],
                    ProcessType = ProcessType.Automated,
                    EventDate = VaultService.randomLocalDateTime(0, 0).minusDays(VaultService.randomInt(0,100).toLong()),
                    Status = HARStatus.OPEN.toString())

            var har2 = har1.copy()
            har2.EventDate = har2.EventDate.plusSeconds(VaultService.randomInt(0, 120).toLong())

            har2.Status = HARStatus.PENDING.toString()
            har2.IsAuthorizationRequired = true

            hars.add(har1)
            hars.add(har2)
        }

        return hars
    }

}