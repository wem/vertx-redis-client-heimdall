package ch.sourcemotion.vertx.redis.client.heimdall.impl.reconnect

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.client.Command
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisConnection
import io.vertx.redis.client.Request

internal interface RedisReconnectProcess {
    fun startReconnectProcess(cause: Throwable, handler: Handler<AsyncResult<Redis>>)
}

internal abstract class AbstractRedisReconnectProcess(
    protected val options: RedisHeimdallOptions,
) : RedisReconnectProcess {
    protected fun Handler<AsyncResult<Redis>>.replyMaxNumberOfAttemptsReached(maxReconnectAttempts: Int) {
        handle(
            Future.failedFuture(
                RedisHeimdallException(
                    Reason.MAX_ATTEMPTS_REACHED,
                    message = "Max number of reconnect attempts \"$maxReconnectAttempts\" to Redis server(s) ${options.endpointsToString()}"
                )
            )
        )
    }
}

internal class NoopRedisReconnectProcess(options: RedisHeimdallOptions) : AbstractRedisReconnectProcess(options) {
    override fun startReconnectProcess(cause: Throwable, handler: Handler<AsyncResult<Redis>>) {
        handler.handle(
            Future.failedFuture(
                RedisHeimdallException(
                    Reason.RECONNECT_DISABLED,
                    "Extended Redis client that's configured for server(s)" +
                            " ${options.endpointsToString()} is not configured for retries"
                )
            )
        )
    }
}

internal class DefaultRedisReconnectProcess(
    private val vertx: Vertx,
    options: RedisHeimdallOptions,
    private val maxReconnectAttempts: Int = options.maxReconnectAttempts,
    private val reconnectInterval: Long = options.reconnectInterval
) : AbstractRedisReconnectProcess(options) {

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultRedisReconnectProcess::class.java)
    }

    override fun startReconnectProcess(cause: Throwable, handler: Handler<AsyncResult<Redis>>) {
        reconnect(handler = handler)
    }

    private fun reconnect(previousAttempts: Int = 0, handler: Handler<AsyncResult<Redis>>) {
        if (maxReconnectAttempts in 1..previousAttempts) {
            logger.warn("Max attempts to reconnect to Redis server(s) ${options.endpointsToString()} reached. Will skip.")
            handler.replyMaxNumberOfAttemptsReached(options.maxReconnectAttempts)
            return
        }
        val client = Redis.createClient(vertx, options)
        client.connect { reconnect ->
            if (reconnect.succeeded()) {
                val connection = reconnect.result()
                connection.verifyConnection { verification ->
                    connection.close()
                    if (verification.succeeded()) {
                        logger.info("Redis client reconnected to server(s) ${options.endpointsToString()}")
                        handler.handle(Future.succeededFuture(client))
                    } else {
                        scheduleReattempt(previousAttempts, handler)
                    }
                }
            } else {
                scheduleReattempt(previousAttempts, handler)
            }
        }
    }

    private fun scheduleReattempt(previousAttempts: Int,handler: Handler<AsyncResult<Redis>>) {
        logger.debug(
            "Unable to reconnect to Redis server(s) ${options.endpointsToString()}. " +
                    "Will retry in $reconnectInterval Milliseconds"
        )
        vertx.setTimer(reconnectInterval) {
            reconnect(previousAttempts + 1, handler)
        }
    }

    private fun RedisConnection.verifyConnection(handler: Handler<AsyncResult<Unit>>) {
        send(Request.cmd(Command.PING)) {
            if (it.succeeded() && it.result().toString() == "PONG") {
                handler.handle(Future.succeededFuture())
            } else {
                handler.handle(Future.failedFuture(it.cause()))
            }
        }
    }
}
