package ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription

import ch.sourcemotion.vertx.redis.client.heimdall.impl.PostReconnectJob
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.connection.RedisSubscriptionHeimdallConnection
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.client.Redis

object SubscriptionPostReconnectJob : PostReconnectJob {

    private val logger = LoggerFactory.getLogger(SubscriptionPostReconnectJob::class.java)

    override fun execute(redis: Redis, handler: Handler<AsyncResult<Unit>>) {
        logger.debug("Execute subscription post reconnect job")
        redis.connect {
            if (it.succeeded()) {
                val connection = it.result()
                if (connection is RedisSubscriptionHeimdallConnection) {
                    // Subscribe to channels after reconnect
                    connection.subscribeAfterReconnect(handler)
                } else {
                    logger.logWrongConnectionType(connection)
                    // We flag here the job as successful, as we can nothing except logging
                    handler.handle(Future.succeededFuture())
                }
            } else {
                logger.warn("Connect failed during subscription post reconnect job")
                // We initiate the reconnect process again
                handler.handle(Future.failedFuture(it.cause()))
            }
        }
    }
}
