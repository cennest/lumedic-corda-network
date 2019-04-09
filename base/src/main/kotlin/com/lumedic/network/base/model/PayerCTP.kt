package com.lumedic.network.base.model

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class HARStatus{
    OPEN,CONFIRMED,PENDING,DENIED
}
@CordaSerializable
class PayerCTP(val ctpCode:String, val payer:String, var authCode:String="", var isAuthRequired:Boolean?=null)
