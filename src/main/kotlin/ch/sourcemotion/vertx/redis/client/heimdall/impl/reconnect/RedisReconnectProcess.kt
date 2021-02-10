package ch.sourcemotion.vertx.redis.client.heimdall.impl.reconnect

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions
import io.vertx.core.*
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.client.Command
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisConnection
import io.vertx.redis.client.Request

internal interface RedisReconnectProcess {
    fun startReconnectProcess(cause: Throwable): Future<Redis>
}

internal abstract class AbstractRedisReconnectProcess(
    protected val options: RedisHeimdallOptions,
) : RedisReconnectProcess

internal class NoopRedisReconnectProcess(options: RedisHeimdallOptions) : AbstractRedisReconnectProcess(options) {
    override fun startReconnectProcess(cause: Throwable): Future<Redis> {
        return Future.failedFuture(
            RedisHeimdallException(
                Reason.RECONNECT_DISABLED,
                "Extended Redis client that's configured for server(s)" +
                        " ${options.endpointsToString()} is not configured for retries"
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

    override fun startReconnectProcess(cause: Throwable): Future<Redis> {
        val promise = Promise.promise<Redis>()
        reconnect(promise)
        return promise.future()
    }

    private fun reconnect(promise: Promise<Redis>, previousAttempts: Int = 0) {
        if (maxReconnectAttempts in 1..previousAttempts) {
            logger.warn("Max attempts to reconnect to Redis server(s) ${options.endpointsToString()} reached. Will skip.")
            promise.fail(
                RedisHeimdallException(
                    Reason.MAX_ATTEMPTS_REACHED,
                    message = "Max number of reconnect attempts \"$maxReconnectAttempts\" to Redis server(s) ${options.endpointsToString()}"
                )
            )
        } else {
            val client = Redis.createClient(vertx, options.redisOptions)
            client.connect()
                .onSuccess { connection ->
                    connection.verifyConnection { verification ->
                        connection.close()
                        if (verification.succeeded()) {
                            logger.info("Redis client reconnected to server(s) ${options.endpointsToString()}")
                            promise.complete(client)
                        } else {
                            scheduleReattempt(promise, previousAttempts)
                        }
                    }
                }.onFailure { scheduleReattempt(promise, previousAttempts) }

        }
    }

    private fun scheduleReattempt(promise: Promise<Redis>, previousAttempts: Int) {
        logger.debug(
            "Unable to reconnect to Redis server(s) ${options.endpointsToString()}. " +
                    "Will retry in $reconnectInterval Milliseconds"
        )
        vertx.setTimer(reconnectInterval) {
            reconnect(promise, previousAttempts + 1)
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
