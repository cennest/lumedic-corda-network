package com.lumedic.network.base.state

import com.lumedic.network.base.schema.HARStateSchemaV1
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.SerializationWhitelist
import java.time.Instant
import java.util.*


// *****************
// * Contract Code *
// *****************


class SerializationWhiteList : SerializationWhitelist {

    override val whitelist: List<Class<*>> = listOf(java.sql.Date::class.java, java.util.Date::class.java,
            Instant::class.java)

}


data class HARState (val status: String,
                     val parties:List<Party>,
                     val description:String,
                     val harID:String,
                     val cptCode:String,
                     val provider:String,
                     val payer:String,
                     val branch:String,
                     val authCode:String,
                     override val linearId: UniqueIdentifier,
                     val eventTime: Date,
                     val updatedDate:Date= Date.from(Instant.now()),
                     val isAuthRequired:Boolean?=null) : LinearState, QueryableState {

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(HARStateSchemaV1)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {

        return when (schema) {
            is HARStateSchemaV1 -> HARStateSchemaV1.PersistentHARState(
                    this.description,this.status,this.harID,this.cptCode,this.provider,this.payer,this.branch,this.eventTime,this.updatedDate, this.isAuthRequired,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }


    override val participants get()= parties
}