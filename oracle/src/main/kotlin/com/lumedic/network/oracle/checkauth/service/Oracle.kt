package com.lumedic.network.oracle.checkauth.service

import com.lumedic.network.base.contract.HARStateContract
import com.lumedic.network.base.model.PayerCTP
import com.lumedic.network.oracle.checkauth.db.OracleDbService
import com.lumedic.network.oracle.checkauth.http.RestService
import net.corda.core.contracts.Command
import net.corda.core.crypto.TransactionSignature
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction

@CordaService
class Oracle(private val services: ServiceHub) : SingletonSerializeAsToken() {
    private val myKey = services.myInfo.legalIdentities.first().owningKey

    fun queryCPTCode( ctpPayer:PayerCTP): PayerCTP {


        if(ctpPayer.payer.equals("PHP",true)){
            val restService = services.cordaService(RestService::class.java)
            ctpPayer.isAuthRequired = restService.getAuthorizationRequiredFlag(ctpPayer.ctpCode, ctpPayer.payer)
        } else {
            val oracleDbService = services.cordaService(OracleDbService::class.java)
            ctpPayer.isAuthRequired = oracleDbService.getAuthorizationRequiredFlag(ctpPayer.ctpCode, ctpPayer.payer)
        }

        return ctpPayer
    }
    fun sign(ftx: FilteredTransaction): TransactionSignature {
        // Check the partial Merkle tree is valid.
        ftx.verify()

        /** Returns true if the component is an Create command that:
         *  - States the correct prime
         *  - Has the oracle listed as a signer
         */
        fun isCommandWithCorrectAuthCodeAndIAmSigner(elem: Any) = when {
            elem is Command<*> && elem.value is HARStateContract.CheckAuth -> {
                val cmdData = elem.value as HARStateContract.CheckAuth
                val payerCTP = PayerCTP(cmdData.cptCode, cmdData.payer)

                myKey in elem.signers && queryCPTCode(payerCTP).isAuthRequired == cmdData.isAuthRequired
            }
            else -> false
        }

        // Is it a Merkle tree we are willing to sign over?
        val isValidMerkleTree = ftx.checkWithFun(::isCommandWithCorrectAuthCodeAndIAmSigner)

        if (isValidMerkleTree) {
            return services.createSignature(ftx, myKey)
        } else {
            throw IllegalArgumentException("Oracle signature requested over invalid transaction.")
        }
    }
}