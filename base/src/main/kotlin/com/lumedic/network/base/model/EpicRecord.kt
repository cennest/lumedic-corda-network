package com.lumedic.network.base.model

import java.util.*

data class EpicRecord(val provider:String,
                      val payer:String,
                      val branch:String,
                      val desc:String,
                      val cptCode:String,
                      var scheduledDate: Date? = null)