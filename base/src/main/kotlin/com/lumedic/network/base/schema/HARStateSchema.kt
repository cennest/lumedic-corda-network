package com.lumedic.network.base.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object HARStateSchema

object HARStateSchemaV1: MappedSchema(HARStateSchema.javaClass,1, listOf(PersistentHARState::class.java))
{
    @Entity
    @Table(name="har_states")
    class PersistentHARState(
            @Column(name="description")
            var description:String="",
            @Column(name="status")
            var status:String="",
            @Column(name="harid")
            var harID:String="",
            @Column(name="cptCode")
            var cptCode:String="",
            @Column(name="provider")
            var provider:String="",
            @Column(name="payer")
            var payer:String="",
            @Column(name="branch")
            var branch:String="",
            @Column (name="eventDate")
            var eventDate:Date=  Date.from(Instant.now()),
            @Column (name="updatedDate")
            var updatedDate:Date=  Date.from(Instant.now()),
            @Column(name="isAuthorizationReq")
            var isAuthorizationRequired:Boolean?=null,
            @Column(name = "linear_id")
            var linearId: UUID

    ): PersistentState()
    {
        constructor():this("","","","","","","", Date.from(Instant.now()),Date.from(Instant.now()),null, UUID.randomUUID())
    }
}

