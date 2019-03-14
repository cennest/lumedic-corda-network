package com.lumedic.network.oracle.checkauth.http


import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.google.common.reflect.TypeToken
import com.google.gson.Gson


import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import java.io.Reader


data class PayerInfo(val CPTCode: String = "", val PayerCode: String = "", val AuthRequired : Boolean = false) {

    class Deserializer : ResponseDeserializable<PayerInfo> {
        override fun deserialize(reader: Reader) = Gson().fromJson(reader, PayerInfo::class.java)
    }

    class ListDeserializer : ResponseDeserializable<List<PayerInfo>> {
        override fun deserialize(reader: Reader): List<PayerInfo> {
            val type = object : TypeToken<List<PayerInfo>>() {}.type
            return Gson().fromJson(reader, type)
        }
    }
}

/**
 * A Http service subclass for handling rest requests.
 *
 * @param services The node's service hub.
 */
@CordaService
class RestService(private val services: ServiceHub) : SingletonSerializeAsToken(){

    companion object {
        val log = loggerFor<RestService>()
    }

    init {
        FuelManager.instance.basePath = "https://lumedicpayer.azurewebsites.net/api"
    }

    fun getAuthorizationRequiredFlag(cpt_code: String, payer: String): Boolean {

        val payerInfo = PayerInfo(cpt_code,payer)
        val body = Gson().toJson(payerInfo)

        val (_, _, result) = Fuel.post("/payer")
                .header("Content-Type" to "application/json")
                .body(body, Charsets.UTF_8)
                .responseObject(PayerInfo.Deserializer())

        val data = result.component1()
        val error = result.component2()

        log.info("RestService: Payer:$payer, CPT:$cpt_code, Data: ${data.toString()}, Error : ${error.toString()}")
        return data?.AuthRequired ?: false
    }
}