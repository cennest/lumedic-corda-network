package com.lumedic.network.payer.db

import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService

/**
 * A database service subclass for handling a table of PayerAuthBook.
 *
 * @param services The node's service hub.
 */
@CordaService
class PayerDbService(services: ServiceHub) : DatabaseService(services) {
    init {
        setUpStorage()
    }

    /**
     * Adds a PayeeAuthBook record
     */
    fun addPayeeAuthBook(cpt_code: String, auth_code: String) {

        val query = "INSERT INTO PayerAuthBook(CPT_Code, Auth_Code) VALUES(?, ?)"
        val params = mapOf(1 to cpt_code, 2 to auth_code)
        executeUpdate(query, params)
        log.info("Payer- CPT:$cpt_code added to PayerAuthBook table.")
    }

    /**
     * Updates the PayeeAuthBook record
     */
    fun updatePayeeAuthBook(cpt_code: String, auth_code: String) {
        val query = "UPDATE PayerAuthBook SET Auth_Code = ? WHERE CPT_Code = ?"
        val params = mapOf(1 to cpt_code, 2 to auth_code)
        executeUpdate(query, params)
        log.info("Payer- CPT:$cpt_code updated to PayerAuthBook table.")
    }

    /**
     * Retrieves PayeeAuthBook Based on search
     */
    fun getAuthorizationCodeForCPT(cpt_code: String): String? {

        val query = "SELECT Auth_Code FROM PayerAuthBook WHERE CPT_Code = ?"
        val params = mapOf(1 to cpt_code)
        val results = executeQuery(query, params, { it -> it.getString("Auth_Code") })

        if (results.isEmpty()) {
            return null
        }

        val value = results.single()
        return value
    }

    fun flushPayeeAuthBook(){
        val query = "TRUNCATE TABLE PayerAuthBook"
        executeUpdate(query, emptyMap())
        log.info("PayerBook table record deleted")
    }

    private fun seed(){

        flushPayeeAuthBook()

        var csvdata = listOf(
                "0002A,hu3h2a",
                "0004A,kn8y0n",
                "0006A,agc6jh",
                "0009T,oplu4g",
                "003GT,zxbh5t",
                "00987,nmhu4h",
                "00467,louy4d",
                "00432,tfv4hu",
                "00456,nmhu4h",
                "00356,louy4d",
                "00345,tfv4hu",

                "0002U,3khbn2",
                "0004U,vnbj3a",
                "0005U,abjh4c",
                "0009M,3khbn2",
                "0047U,vnbj3a"

        )

        for (item in csvdata){
            val rowParts = item.split(',')

            val cptCode = rowParts[0].trim()
            val authCode = rowParts[1].trim()

            addPayeeAuthBook(cptCode, authCode)
        }
    }

    /**
     * Initialises the table of crypto values.
     */
    private fun setUpStorage() {
        val query = """
            CREATE TABLE IF NOT EXISTS PayerAuthBook(
                PayerAuthBookID BIGINT AUTO_INCREMENT PRIMARY KEY,
                CPT_Code        VARCHAR(100) NOT NULL,
                Auth_Code       VARCHAR(100) NOT NULL
            )"""

        executeUpdate(query, emptyMap())
        log.info("Created PayerAuthBook table.")
        seed()
        log.info("PayerAuthBook Record Seeded")
    }
}