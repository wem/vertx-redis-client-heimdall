package ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription

import ch.sourcemotion.vertx.redis.client.heimdall.impl.PostReconnectJob
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.connection.RedisHeimdallSubscriptionConnection
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.client.Redis

object SubscriptionPostReconnectJob : PostReconnectJob {

    private val logger = LoggerFactory.getLogger(SubscriptionPostReconnectJob::class.java)

    override fun execute(redis: Redis) : Future<Unit> {
        logger.debug("Execute subscription post reconnect job")
        val promise = Promise.promise<Unit>()
        redis.connect()
            .onSuccess { connection ->
                if (connection is RedisHeimdallSubscriptionConnection) {
                    // Subscribe to channels after reconnect
                    connection.subscribeAfterReconnect()
                        .onSuccess { promise.complete() }
                        .onFailure { promise.fail(it) }
                } else {
                    logger.logWrongConnectionType(connection)
                    // We flag here the job as successful, as we can nothing except logging
                    promise.complete()
                }
            }
            .onFailure { promise.tryFail(it) }
        return promise.future()
    }
}
