package com.lumedic.network.db

import com.lumedic.network.base.model.HARStatus
import com.lumedic.network.braid.VaultService
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService


/**
 * A database service subclass for handling db operations.
 *
 * @param services The node's service hub.
 */
@CordaService
class ProviderDbService(services: ServiceHub) : DatabaseService(services) {

    /**
     * Retrieves HarStatus
     */
    fun getHarStatusCount(): VaultService.ConsolidatedRecordCount? {

        val query = """
            SELECT
            MAX(OPEN) OPEN,
            MAX(PENDING) PENDING,
            MAX(CONFIRMED) CONFIRMED,
            MAX(DENIED) DENIED
            FROM (
             SELECT
                CASE WHEN STATUS = 'OPEN' THEN COUNT(*)  ELSE 0 END "OPEN",
                CASE WHEN STATUS = 'PENDING'  THEN COUNT(*)   ELSE 0 END "PENDING",
                CASE WHEN STATUS = 'CONFIRMED'  THEN COUNT(*) ELSE 0 END "CONFIRMED",
                CASE WHEN STATUS = 'DENIED'  THEN COUNT(*)   ELSE 0 END "DENIED"
              FROM HAR_STATES GROUP BY STATUS)

        """.trimIndent()

        val params = emptyMap<Int, Any>()
        val results = executeQuery(query, params) { it ->
            VaultService.ConsolidatedRecordCount(listOf(
                    VaultService.RecordCount(HARStatus.OPEN.toString(),it.getLong(HARStatus.OPEN.toString())),
                    VaultService.RecordCount(HARStatus.PENDING.toString(),it.getLong(HARStatus.PENDING.toString())),
                    VaultService.RecordCount(HARStatus.DENIED.toString(),it.getLong(HARStatus.DENIED.toString())),
                    VaultService.RecordCount(HARStatus.CONFIRMED.toString(),it.getLong(HARStatus.CONFIRMED.toString()))
            ))}

        if (results.isEmpty()) {
            return null
        }

        val value = results.single()
        return value
    }

}