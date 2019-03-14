package com.lumedic.network.oracle.checkauth.db

import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService

/**
 * A database service subclass for handling a table of PayerBook.
 *
 * @param services The node's service hub.
 */
@CordaService
class OracleDbService(services: ServiceHub) : DatabaseService(services) {
    init {
        setUpStorage()
    }

    /**
     * Adds a PayeeBook record
     */
    fun addPayeeBook(cpt_code: String, payer: String, authReq : Boolean) {

        val query = "INSERT INTO PayerBook(CPT_Code, Payer_Code, AuthRequired) VALUES(?, ?, ?)"
        val params = mapOf(1 to cpt_code, 2 to payer, 3 to authReq)
        executeUpdate(query, params)
        log.info("Payer $payer - CPT:$cpt_code added to PayerBook table.")
    }

    /**
     * Updates the PayeeBook record
     */
    fun updatePayeeBook(cpt_code: String, payer: String, authReq : Boolean) {
        val query = "UPDATE PayerBook SET AuthRequired = ? WHERE CPT_Code = ? AND Payer_Code = ?"
        val params = mapOf(1 to cpt_code, 2 to payer, 3 to authReq)
        executeUpdate(query, params)
        log.info("Payer $payer - CPT:$cpt_code updated to PayerBook table.")
    }

    /**
     * Retrieves PayeeBook Based on search
     */
    fun getAuthorizationRequiredFlag(cpt_code: String, payer: String): Boolean {

        val query = "SELECT AuthRequired FROM PayerBook WHERE CPT_Code = ? AND Payer_Code = ?"
        val params = mapOf(1 to cpt_code, 2 to payer)
        val results = executeQuery(query, params, { it -> it.getBoolean("AuthRequired") })

        if (results.isEmpty()) {
            return false
        }

        val value = results.single()
        return value
    }

    fun flushPayerBook(){
        val query = "TRUNCATE TABLE PayerBook"
        executeUpdate(query, emptyMap())
        log.info("PayerBook table record deleted")
    }

    private fun seed(){

        flushPayerBook()

        var csvdata = listOf(
                "0002A,Aetna,True",
                "0004A,Aetna,True",
                "0006A,Aetna,True",
                "0009T,Aetna,False",
                "003GT,Aetna,False",
                "004FT,Aetna,False",
                "006YU,Aetna,False",
                "0130I,Aetna,False",


                "00987,UHC,True",
                "00467,UHC,True",
                "00432,UHC,True",
                "00456,UHC,False",
                "00356,UHC,False",
                "00345,UHC,False",
                "00645,UHC,True",
                "01334,UHC,True",


                "0002U,PHP,True",
                "0004U,PHP,True",
                "0005U,PHP,True",
                "0009M,PHP,False",
                "0030T,PHP,False",
                "0042T,PHP,False",
                "0047U,PHP,True",
                "0052T,PHP,True",
                "0058T,PHP,True"
        )

        for (item in csvdata){
            val rowParts = item.split(',')

            val cptCode = rowParts[0].trim()
            val payer = rowParts[1].trim()
            val authFlag = rowParts[2].trim().toBoolean()

            addPayeeBook(cptCode, payer, authFlag)
        }
    }

    /**
     * Initialises the table of crypto values.
     */
    private fun setUpStorage() {
        val query = """
            CREATE TABLE IF NOT EXISTS PayerBook(
                PayerBookID BIGINT AUTO_INCREMENT PRIMARY KEY,
                CPT_Code    VARCHAR(100) NOT NULL,
                Payer_Code  VARCHAR(100) NOT NULL,
                AuthRequired BIT NOT NULL
            )"""

        executeUpdate(query, emptyMap())
        log.info("Created PayerBook table.")
        seed()
        log.info("PayerBook Record Seeded")
    }
}