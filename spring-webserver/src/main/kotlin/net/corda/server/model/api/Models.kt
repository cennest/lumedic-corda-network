package net.corda.server.model.api

import com.lumedic.network.base.state.HARState
import net.corda.core.contracts.StateAndRef

// Data Entities
data class HARRecordResults(val totalRecords:Long,val records: List<StateAndRef<HARState>>)
data class RecordCount(val status: String, val count:Long)
data class ConsolidatedRecordCount(val recordCounts: List<RecordCount>)