package com.lumedic.network.braid

import io.vertx.core.Vertx
import rx.Observer
import rx.Subscription
import rx.observables.SyncOnSubscribe
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

open class SubscriberStateRefresher(val vertx: Vertx){

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm:ss")
    }

    init {
        setUpHeartbeat()
    }

    private fun setUpHeartbeat(){

        // create a timer publisher to the eventbus
        vertx.setPeriodic(5000) {
            vertx.eventBus().publish("heartbeat", timeFormat.format(Date()))
        }
    }
}


open class IndependentSubscriber(private val vertx: Vertx) {

    private var innerSub : Subscription?

    companion object {

        val executor: Executor = Executors.newFixedThreadPool(1)!!
    }

    init {
        innerSub = handleSubscribeEvent()
        val consumer = vertx.eventBus().consumer<String>("heartbeat")
        consumer.handler {
            if(!isActive()) {
                consumer.unregister()
                handleUnsubscribeEvent()
            }
        }
    }

    open fun isActive(): Boolean = innerSub?.isUnsubscribed ?: true
    open fun handleSubscribeEvent() : Subscription? = null
    open fun handleUnsubscribeEvent() { innerSub?.unsubscribe(); innerSub = null}
    fun <T> notify(update : T) = onNotify(update) //executor.execute { onNotify(update) }
    open fun <T> onNotify(update : T) = Unit
}