package com.lumedic.network.braid

import co.paralleluniverse.common.util.Pair
import com.lumedic.network.base.model.HARStatus
import com.lumedic.network.db.DatabaseService
import com.lumedic.network.db.ProviderDbService
import com.lumedic.network.entity.*
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.YearMonth
import java.util.concurrent.CountDownLatch


data class Page(var PageSize : Int, var PageNumber : Int, var TotalRecord : Long)
data class PageDataSet<T>(val Page : Page?, val Rows : List<T> = emptyList())
data class HarStateCount(val Open : Long, val Pending : Long, val Confirmed : Long, val Denied : Long)

data class GroupDataRow(val Provider : String, val Payer : String, val Branch : String,
                        val Year : Int, val Month : Int,
                        val Auto : Int, val AutoTime : Double, val AvgAutoTime : Double,
                        val Manual : Int, val ManualTime : Double, val AvgManualTime : Double,
                        val Type : String)

data class DataRow(val Provider : String, val Payer : String, val Branch : String,
                   val Year : Int, val Month : Int,
                   val IsAutomated : Boolean, val Time : Double, val HarID : String)


data class AnalysisSearch<T>(val Provider:String, val FromState:String, val ToState:String, val FromDate:String,val ToDate:String, var Page:Page? = null, var Result : PageDataSet<T>? = null)


@CordaService
class ReportService(services: ServiceHub) : DatabaseService(services) {

    companion object {
        const val preauth_expected_time_min = 5.00
        const val preauth_manual_cost = 5.00

        fun Double.convertSecondToMinute() : Double {
            return (this / 60).round()
        }

        fun Double.convertSecondToDay() : Double {
            return (this / (60 * 60 * 24)).round()
        }

        fun Double.convertMinuteToDay() : Double {
            return (this / (60 * 24)).round()
        }

        fun Double.convertDayToMinute() : Double {
            return (this * (60 * 24)).round()
        }

        fun Double.round() : Double {
            val df = DecimalFormat("#.##")
            df.roundingMode = RoundingMode.CEILING
            return df.format(this).toDouble()
        }

        fun Double.convertToProcessStateType() : ProcessStateType {

            val result = this.compareTo(preauth_expected_time_min)
            val state = when {
                result > 0 -> ProcessStateType.Worsening
                result < 0 -> ProcessStateType.Improvement
                else -> ProcessStateType.NoChange
            }
            return state
        }



        fun computePreAuthorization(manualCount : Int, manualTime: Double, autoCount:Int, autoTime:Double) : Pair<Double, ProcessStateType> {

            if(manualCount == 0 || autoCount == 0) {
                return Pair(0.0, ProcessStateType.NoChange)
            }

            val manual_avg_time = (manualTime / manualCount).convertSecondToMinute()
            val auto_avg_time = (autoTime / autoCount).convertSecondToMinute()

            val avg_time_diff = manual_avg_time - auto_avg_time

            val avg_day_diff = (avg_time_diff * autoCount).convertMinuteToDay()
            val state = avg_time_diff.convertToProcessStateType()

            return Pair(avg_day_diff, state)
        }

    }

    init {
        setupReportingData()

        /*
        val s1 = AnalysisSearch<GroupDataRow>(
                Provider = "PSJH",
                FromState = "OPEN",
                ToState = "CONFIRMED",
                FromDate = "2018-01-01",
                ToDate = "2020-01-01"
        )

        val s2 = AnalysisSearch<DataRow>(
                Provider = "PSJH",
                FromState = "OPEN",
                ToState = "CONFIRMED",
                FromDate = "2018-01-01",
                ToDate = "2020-01-01",
                Page = Page(100,1,0)
        )

        val a = getHarStateCount()
        val b = fetchProviderGroupAnalysis(s1)
        val c = fetchProviderHarAnalysis(s2)
        */
    }

    private fun setupReportingData(){
        val query = """
            CREATE TABLE IF NOT EXISTS Test_Har_States (
                ID          BIGINT AUTO_INCREMENT PRIMARY KEY,
                HarID       VARCHAR(100) NOT NULL,
                Provider    VARCHAR(100) NOT NULL,
                Payer       VARCHAR(100) NOT NULL,
                Branch      VARCHAR(100) NOT NULL,
                Automated   BIT NOT NULL,
                EventDate   Timestamp NOT NULL,
                Status      VARCHAR(100) NOT NULL
             )"""

        executeUpdate(query, emptyMap())
        DatabaseService.log.info("Created Test_Har_States table.")
    }

    fun addHarRecord(record : HarRecord){
        val query = "INSERT INTO Test_Har_States(HarID, Provider, Payer, Branch, Automated, EventDate, STATUS) VALUES(?, ?, ?, ?, ?, ?, ? )"
        val params = mapOf(1 to record.HarID, 2 to record.Provider, 3 to record.Payer, 4 to record.Branch, 5 to (record.ProcessType == ProcessType.Automated), 6 to record.EventDate, 7 to record.Status )
        executeUpdate(query, params)
    }

    private fun getHarStateCount() : HarStateCount {

        val query = """
            SELECT
            COALESCE(MAX(OPEN), 0) OPEN,
            COALESCE(MAX(PENDING), 0) PENDING,
            COALESCE(MAX(CONFIRMED), 0) CONFIRMED,
            COALESCE(MAX(DENIED), 0) DENIED
            FROM (
             SELECT
                CASE WHEN STATUS = 'OPEN' THEN COUNT(*)  ELSE 0 END "OPEN",
                CASE WHEN STATUS = 'PENDING'  THEN COUNT(*)   ELSE 0 END "PENDING",
                CASE WHEN STATUS = 'CONFIRMED'  THEN COUNT(*) ELSE 0 END "CONFIRMED",
                CASE WHEN STATUS = 'DENIED'  THEN COUNT(*)   ELSE 0 END "DENIED"
              FROM HAR_STATES GROUP BY STATUS
            )
        """.trimIndent()

        val params = emptyMap<Int, Any>()
        val results = executeQuery(query, params) { it ->
            HarStateCount(
                    Open = it.getLong(HARStatus.OPEN.toString()),
                    Pending = it.getLong(HARStatus.PENDING.toString()),
                    Confirmed = it.getLong(HARStatus.CONFIRMED.toString()),
                    Denied = it.getLong(HARStatus.DENIED.toString())
            )
        }

        return results.single()
    }

    private fun fetchProviderGroupAnalysis(search : AnalysisSearch<GroupDataRow>): AnalysisSearch<GroupDataRow> {

        val query = """
            SELECT
                PROVIDER,
                PAYER,
                BRANCH,
                YEAR,
                MONTH,
                SUM(AUTO) AUTO,
                SUM(AUTO_TIME) AUTO_TIME,
                CASE WHEN SUM(AUTO)=0 THEN 0 ELSE ROUND(SUM(AUTO_TIME) / SUM(AUTO), 3) END AVG_AUTO_TIME,
                SUM(MANUAL) MANUAL,
                SUM(MANUAL_TIME) MANUAL_TIME,
                CASE WHEN SUM(MANUAL)=0 THEN 0 ELSE ROUND(SUM(MANUAL_TIME) / SUM(MANUAL), 3) END AVG_MANUAL_TIME,
                '${search.FromState}-${search.ToState}' TYPE
                FROM (
                 SELECT
                    DS.PROVIDER, DS.PAYER, DS.BRANCH, DS.YEAR, DS.MONTH,
                    CASE WHEN AUTOMATED = 1 THEN COUNT(*) ELSE 0 END AUTO,
                    CASE WHEN AUTOMATED = 0 THEN COUNT(*) ELSE 0 END MANUAL,
                    CASE WHEN AUTOMATED = 1 THEN SUM(DS.PROCESSTIME) ELSE 0 END AUTO_TIME,
                    CASE WHEN AUTOMATED = 0 THEN SUM(DS.PROCESSTIME) ELSE 0 END MANUAL_TIME
                  FROM (SELECT H1.HarID, H1.PROVIDER,  H1.PAYER, H1.BRANCH, 1 AUTOMATED,
                               YEAR(H1.EVENTDATE) YEAR, MONTH(H1.EVENTDATE) MONTH, DATEDIFF(SECOND, H1.UPDATEDDATE , H2.UPDATEDDATE) PROCESSTIME
                        FROM HAR_STATES H1
                        INNER JOIN HAR_STATES H2 ON H2.HARID = H1.HARID
                        WHERE H1.STATUS = '${search.FromState}' AND H2.STATUS = '${search.ToState}'
                        AND (LENGTH(COALESCE('${search.Provider}','')) = 0 OR (H1.PROVIDER = '${search.Provider}' AND H1.PROVIDER = H2.PROVIDER))
                        AND H1.EVENTDATE BETWEEN '${search.FromDate}' AND '${search.ToDate}'
                        AND H2.EVENTDATE BETWEEN '${search.FromDate}' AND '${search.ToDate}'
                   UNION ALL
                        SELECT H1.HarID, H1.PROVIDER,  H1.PAYER, H1.BRANCH, 0 AUTOMATED,
                               YEAR(H1.EVENTDATE) YEAR, MONTH(H1.EVENTDATE) MONTH, DATEDIFF(SECOND, H1.EVENTDATE , H2.EVENTDATE) PROCESSTIME
                        FROM TEST_HAR_STATES H1
                        INNER JOIN TEST_HAR_STATES H2 ON H2.HARID = H1.HARID
                        WHERE H1.STATUS = '${search.FromState}' AND H2.STATUS = '${search.ToState}'
                        AND (LENGTH(COALESCE('${search.Provider}','')) = 0 OR (H1.PROVIDER = '${search.Provider}' AND H1.PROVIDER = H2.PROVIDER))
                        AND H1.EVENTDATE BETWEEN '${search.FromDate}' AND '${search.ToDate}'
                        AND H2.EVENTDATE BETWEEN '${search.FromDate}' AND '${search.ToDate}'
                        ) DS
                GROUP BY DS.PROVIDER, DS.PAYER, DS.BRANCH, DS.AUTOMATED, DS.YEAR, DS.MONTH) MS
                GROUP BY MS.PROVIDER, MS.PAYER, MS.BRANCH, MS.MONTH, MS.YEAR
        """.trimIndent()

        val params = emptyMap<Int, Any>()
        val rows = executeQuery(query, params) { it ->
            GroupDataRow(
                    Provider = it.getString("PROVIDER"),
                    Payer = it.getString("PAYER"),
                    Branch = it.getString("BRANCH"),
                    Year = it.getInt("YEAR"),
                    Month = it.getInt("MONTH"),
                    Auto = it.getInt("AUTO"),
                    AutoTime = it.getDouble("AUTO_TIME"),
                    AvgAutoTime = it.getDouble("AVG_AUTO_TIME"),
                    Manual = it.getInt("MANUAL"),
                    ManualTime = it.getDouble("MANUAL_TIME"),
                    AvgManualTime = it.getDouble("AVG_MANUAL_TIME"),
                    Type = it.getString("TYPE")
            )
        }

        search.Result = PageDataSet(Rows = rows, Page = null)
        return search
    }

    private fun fetchProviderHarAnalysis(search : AnalysisSearch<DataRow>): AnalysisSearch<DataRow> {

        var inputPage = search.Page ?: Page(10, 1, 0)
        if(inputPage.TotalRecord == 0.toLong()) {

            val countQuery = """
                SELECT COUNT(H1.HarID) TotalRecord
                FROM HAR_STATES H1
                INNER JOIN HAR_STATES H2 ON H2.HARID = H1.HARID
                WHERE H1.STATUS = '${search.FromState}' AND H2.STATUS = '${search.ToState}'
                AND (LENGTH(COALESCE('${search.Provider}','')) = 0 OR (H1.PROVIDER = '${search.Provider}' AND H1.PROVIDER = H2.PROVIDER))
                AND H1.UPDATEDDATE BETWEEN '${search.FromDate}' AND '${search.ToDate}'
                AND H2.UPDATEDDATE BETWEEN '${search.FromDate}' AND '${search.ToDate}'
            """.trimIndent()

            inputPage.TotalRecord = executeQuery(countQuery, emptyMap()) { it -> it.getLong("TotalRecord")}.single()
        }


        val query = """
            SELECT  H1.HarID, H1.PROVIDER,  H1.PAYER, H1.BRANCH, 1 AUTOMATED,
                    YEAR(H1.EVENTDATE) YEAR, MONTH(H1.EVENTDATE) MONTH, DATEDIFF(SECOND, H1.UPDATEDDATE , H2.UPDATEDDATE) PROCESSTIME
            FROM HAR_STATES H1
            INNER JOIN HAR_STATES H2 ON H2.HARID = H1.HARID
            WHERE H1.STATUS = '${search.FromState}' AND H2.STATUS = '${search.ToState}'
            AND (LENGTH(COALESCE('${search.Provider}','')) = 0 OR (H1.PROVIDER = '${search.Provider}' AND H1.PROVIDER = H2.PROVIDER))
            AND H1.UPDATEDDATE BETWEEN '${search.FromDate}' AND '${search.ToDate}'
            AND H2.UPDATEDDATE BETWEEN '${search.FromDate}' AND '${search.ToDate}'
            LIMIT ${inputPage.PageSize} OFFSET ${(inputPage.PageNumber-1) * inputPage.PageSize}
        """.trimIndent()

        val params = emptyMap<Int, Any>()
        val rows = executeQuery(query, params) { it ->
            DataRow(
                    Provider = it.getString("PROVIDER"),
                    Payer = it.getString("PAYER"),
                    Branch = it.getString("BRANCH"),
                    Year = it.getInt("YEAR"),
                    Month = it.getInt("MONTH"),
                    HarID = it.getString("HarID"),
                    IsAutomated = it.getBoolean("AUTOMATED"),
                    Time = it.getDouble("PROCESSTIME")
            )
        }

        search.Result = PageDataSet(Rows = rows, Page = inputPage)
        search.Page = inputPage
        return search
    }

    fun List<GroupDataRow>.getProviderAnalysis() : List<Provider> {

        val group = this.groupBy { it.Provider }
        val providers = group.map { item ->

            val m_time = item.value.map { m ->  m.ManualTime}.sum()
            val m_count = item.value.map { m ->  m.Manual}.sum()

            val a_time = item.value.map { m ->  m.AutoTime}.sum()
            var a_count = item.value.map { m ->  m.Auto}.sum()

            val result = computePreAuthorization(m_count, m_time, a_count, a_time)
            val days = result.first
            val state = result.second

            var reg_field = Field(Name = "Registration", Value = 2.8, Percent = 10.0)
            var reg_process = com.lumedic.network.entity.Process(Order = 0, Name = "Scheduled", Fields = listOf(reg_field))

            var pre_reg_field = Field(Name = "Pre-Authorization", Value = days, Percent = 10.0, State = state)
            var pre_reg_process = com.lumedic.network.entity.Process(Order = 1, Name = "Pre-Registered", Fields = listOf(pre_reg_field))

            var authorized_process = com.lumedic.network.entity.Process(Order = 2, Name = "Authorized", Fields = listOf())
            var admitted_process = com.lumedic.network.entity.Process(Order = 3, Name = "Admitted", Fields = listOf())
            var discharged_process = com.lumedic.network.entity.Process(Order = 4, Name = "Discharged", Fields = listOf())
            var claim_process = com.lumedic.network.entity.Process(Order = 5, Name = "Claim Submitted", Fields = listOf())
            var remittance_process = com.lumedic.network.entity.Process(Order = 6, Name = "Remittance Received", Fields = listOf())
            var billed_process = com.lumedic.network.entity.Process(Order = 7, Name = "Patient Billed BAI", Fields = listOf())


            Provider(item.key, listOf(reg_process, pre_reg_process, authorized_process, admitted_process, discharged_process, claim_process, remittance_process, billed_process))
        }

        return providers
    }

    fun List<GroupDataRow>.getBranchAnalysis() : List<Branch>{

        val group = this.groupBy { it.Branch }
        val branches = group.map { item ->

            val m_time = item.value.map { m ->  m.ManualTime}.sum()
            val a_time = item.value.map { m ->  m.AutoTime}.sum()

            val m_count = item.value.map { m ->  m.Manual}.sum()
            val a_count = item.value.map { m ->  m.Auto}.sum()

            val result = computePreAuthorization(m_count, m_time, a_count, a_time)
            val days = result.first
            val state = result.second

            var reg_field = Field(Name = "Registration", Value = 2.8, Percent = 10.0)
            var reg_process = com.lumedic.network.entity.Process(Order = 0, Name = "Scheduled", Fields = listOf(reg_field))

            var pre_reg_field = Field(Name = "Pre-Authorization", Value = days, Percent = 10.0, State = state)
            var pre_reg_process = com.lumedic.network.entity.Process(Order = 1, Name = "Pre-Registered", Fields = listOf(pre_reg_field), State = state)

            var authorized_process = com.lumedic.network.entity.Process(Order = 2, Name = "Authorized", Fields = listOf())
            var admitted_process = com.lumedic.network.entity.Process(Order = 3, Name = "Admitted", Fields = listOf())
            var discharged_process = com.lumedic.network.entity.Process(Order = 4, Name = "Discharged", Fields = listOf())
            var claim_process = com.lumedic.network.entity.Process(Order = 5, Name = "Claim Submitted", Fields = listOf())
            var remittance_process = com.lumedic.network.entity.Process(Order = 6, Name = "Remittance Received", Fields = listOf())
            var billed_process = com.lumedic.network.entity.Process(Order = 7, Name = "Patient Billed BAI", Fields = listOf())


            Branch(item.key, listOf(reg_process, pre_reg_process, authorized_process, admitted_process, discharged_process, claim_process, remittance_process, billed_process))
        }

        return branches
    }

    fun List<GroupDataRow>.getPayerAnalysis() : List<Payer> {

        val group = this.groupBy { it.Payer }
        val payers = group.map { item ->

            val m_time = item.value.map { m ->  m.ManualTime}.sum()
            val a_time = item.value.map { m ->  m.AutoTime}.sum()

            val m_count = item.value.map { m ->  m.Manual}.sum()
            val a_count = item.value.map { m ->  m.Auto}.sum()

            val result = computePreAuthorization(m_count, m_time, a_count, a_time)
            val days = result.first
            val state = result.second

            var reg_field = Field(Name = "Registration", Value = 2.8, Percent = 10.0)
            var reg_process = com.lumedic.network.entity.Process(Order = 0, Name = "Scheduled", Fields = listOf(reg_field))

            var pre_reg_field = Field(Name = "Pre-Authorization", Value = days, Percent = 10.0, State = state)
            var pre_reg_process = com.lumedic.network.entity.Process(Order = 1, Name = "Pre-Registered", Fields = listOf(pre_reg_field))

            var authorized_process = com.lumedic.network.entity.Process(Order = 2, Name = "Authorized", Fields = listOf())
            var admitted_process = com.lumedic.network.entity.Process(Order = 3, Name = "Admitted", Fields = listOf())
            var discharged_process = com.lumedic.network.entity.Process(Order = 4, Name = "Discharged", Fields = listOf())
            var claim_process = com.lumedic.network.entity.Process(Order = 5, Name = "Claim Submitted", Fields = listOf())
            var remittance_process = com.lumedic.network.entity.Process(Order = 6, Name = "Remittance Received", Fields = listOf())
            var billed_process = com.lumedic.network.entity.Process(Order = 7, Name = "Patient Billed BAI", Fields = listOf())

            Payer(item.key, listOf(reg_process, pre_reg_process, authorized_process, admitted_process, discharged_process, claim_process, remittance_process, billed_process))
        }

        return payers
    }

    fun List<DataRow>.getPatientAnalysis() : List<Patient>{

        val group = this.groupBy { it.HarID }
        val patients = group.map { item ->

            val time = item.value.map { m ->  m.Time.convertSecondToMinute() }.sum()
            val days = time.convertMinuteToDay()

            /* Pre-Authorization automation computation*/
            val state = time.convertToProcessStateType()

            var reg_field = Field(Name = "Registration", Value = 2.8, Percent = 10.0, State = ProcessStateType.NoChange)
            var reg_process = com.lumedic.network.entity.Process(Order = 0, Name = "Scheduled", Fields = listOf(reg_field), State = ProcessStateType.NoChange)

            var pre_reg_field = Field(Name = "Pre-Authorization", Value = days, Percent = 0.0, State = state)
            var pre_reg_process = com.lumedic.network.entity.Process(Order = 1, Name = "Pre-Registered", Fields = listOf(pre_reg_field), State = state)

            var authorized_process = com.lumedic.network.entity.Process(Order = 2, Name = "Authorized", Fields = listOf())
            var admitted_process = com.lumedic.network.entity.Process(Order = 3, Name = "Admitted", Fields = listOf())
            var discharged_process = com.lumedic.network.entity.Process(Order = 4, Name = "Discharged", Fields = listOf())
            var claim_process = com.lumedic.network.entity.Process(Order = 5, Name = "Claim Submitted", Fields = listOf())
            var remittance_process = com.lumedic.network.entity.Process(Order = 6, Name = "Remittance Received", Fields = listOf())
            var billed_process = com.lumedic.network.entity.Process(Order = 7, Name = "Patient Billed BAI", Fields = listOf())

            Patient(item.key, listOf(reg_process, pre_reg_process, authorized_process, admitted_process, discharged_process, claim_process, remittance_process, billed_process))
        }

        return patients
    }

    fun List<GroupDataRow>.getPreAuthAnalysis() : PreAuthorization {

        val group = this.groupBy { it.Provider }
        val providers = group.map { item ->

            val m_time = item.value.map { m ->  m.ManualTime}.sum()
            val m_count = item.value.map { m ->  m.Manual}.sum()

            val a_time = item.value.map { m ->  m.AutoTime}.sum()
            val a_count = item.value.map { m ->  m.Auto}.sum()

            val a_result = computePreAuthorization(m_count, m_time, a_count, a_time)
            val a_days = a_result.first
            val a_state = a_result.second

            val total = m_count + a_count

            var m_reg_field = Field(Name = "No-Auth Needed (Manual)", Value = m_count.toDouble(), Percent = 50.0)
            var a_reg_field = Field(Name = "No-Auth Needed (Auto)", Value = a_count.toDouble(), Percent = 50.0)
            var reg_process = com.lumedic.network.entity.Process(Order = 0, Name = "Registration", Fields = listOf(m_reg_field, a_reg_field))


            var m_pre_auth_field = Field(Name = "Authorization Needed (Manual)", Value =  m_count.toDouble(), Percent = ((m_count.toDouble()*100)/total))
            var a_pre_auth_field = Field(Name = "Authorization Needed (Auto)", Value = a_count.toDouble(), Percent = ((a_count.toDouble()*100)/total), State = a_state)
            var pre_auth_process = com.lumedic.network.entity.Process(Order = 1, Name = "Pre-Authorization", Fields = listOf(m_pre_auth_field, a_pre_auth_field), State = a_state)


            PreAuthorization(Items = listOf(reg_process, pre_auth_process))
        }

        return providers.single()
    }

    fun List<GroupDataRow>.getAutomationAnalysis() : AutomationAnalytic {

        val group = this.groupBy { it.Provider }
        val providers = group.map { item ->

            val m_time = item.value.map { m ->  m.ManualTime}.sum()
            val m_count = item.value.map { m ->  m.Manual}.sum()

            val a_time = item.value.map { m ->  m.AutoTime}.sum()
            val a_count = item.value.map { m ->  m.Auto}.sum()

            val a_result = computePreAuthorization(m_count, m_time, a_count, a_time)
            val a_days = a_result.first
            val a_state = a_result.second

            val value_added = (a_days.convertDayToMinute() / preauth_expected_time_min)* preauth_manual_cost
            AutomationAnalytic("$ ${value_added.round()}", "${a_days.round()} d")
        }

        return providers.single()
    }

    fun fetchReportAnalysis(reportType : ReportType, providerName : String, page : Page? = null) : AnalysisReport? {

        synchronized(this){

            val date = LocalDateTime.now()

            val fm = if(reportType == ReportType.MTD) {date.monthValue} else {1}
            val tm = if(reportType == ReportType.MTD) {date.monthValue} else {12}
            val y = date.year

            val fyrMonth = YearMonth.of(y, tm)
            val fd = fyrMonth.lengthOfMonth()

            val searchGroup = AnalysisSearch<GroupDataRow>(
                    Provider = providerName,
                    FromState = "OPEN",
                    ToState = "PENDING",
                    FromDate = "$y-$fm-01",
                    ToDate = "$y-$tm-$fd"
            )

            val searchIndividual = AnalysisSearch<DataRow>(
                    Provider = providerName,
                    FromState = "OPEN",
                    ToState = "PENDING",
                    FromDate = "$y-$fm-01",
                    ToDate = "$y-$tm-$fd",
                    Page = page
            )

            val groupResult = fetchProviderGroupAnalysis(searchGroup).Result!!
            val individualResult = fetchProviderHarAnalysis(searchIndividual).Result!!

            val providers = groupResult.Rows.getProviderAnalysis()
            if(providers.count { it.Name == providerName } == 0) return  null

            var report = AnalysisReport(
                    Type = reportType,
                    Provider = providers.first(){ it.Name == providerName },
                    Payer = groupResult.Rows.getPayerAnalysis(),
                    Branch = groupResult.Rows.getBranchAnalysis(),
                    Har = individualResult.Rows.getPatientAnalysis(),
                    PreAuthorization = groupResult.Rows.getPreAuthAnalysis(),
                    AutomationAnalytic = groupResult.Rows.getAutomationAnalysis()
            )

            return  report
        }
    }
}