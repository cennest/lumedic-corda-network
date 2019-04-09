package com.lumedic.network.braid

import io.vertx.core.Future
import net.corda.core.concurrent.CordaFuture
import net.corda.core.utilities.getOrThrow

/**
 * Handy function that we probably should put in Braid
 * Converts a CordaFuture to a Vertx Future
 */
fun <T> CordaFuture<T>.toVertxFuture(): Future<T> {
    val result = Future.future<T>()
    this.then { f ->
        try {
            result.complete(f.getOrThrow())
        } catch (err: Throwable) {
            result.fail(err)
        }
    }
    return result
}