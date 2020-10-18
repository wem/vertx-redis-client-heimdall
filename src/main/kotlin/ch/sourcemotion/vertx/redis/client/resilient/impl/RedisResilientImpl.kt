package ch.sourcemotion.vertx.redis.client.resilient.impl

import ch.sourcemotion.vertx.redis.client.resilient.RedisResilientOptions
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisConnection

internal class RedisResilientImpl(
    private val vertx: Vertx,
    private val options: RedisResilientOptions,
) : Redis {

    private val logger = LoggerFactory.getLogger(RedisResilientImpl::class.java)

    @Volatile
    private var delegate: Redis = Redis.createClient(vertx, options)

    @Volatile
    private var reconnectingInProgress = false

    private val reconnectingHandler: RedisConnectionFailureHandler = configureRedisConnectionFailureHandler()

    override fun connect(handler: Handler<AsyncResult<RedisConnection>>): Redis {
        delegate.connect { asyncConnection ->
            if (asyncConnection.succeeded()) {
                val connection = ResilientRedisConnection(asyncConnection.result()) {
                    handleConnectionFailure<Unit>(it)
                }
                handler.handle(Future.succeededFuture(connection))
            } else {
                handler.handle(Future.failedFuture(asyncConnection.cause()))
            }
        }
        return delegate
    }

    override fun close() {
        delegate.close()
    }

    private fun <R> handleConnectionFailure(cause: Throwable) {
        // Avoid multiple, parallel reconnection processes
        if (reconnectingInProgress) {
            logger.trace("Avoid multiple reconnect processes for Redis client to server(s) ${options.endpointsToString()}")
            return
        }
        reconnectingInProgress = true
        delegate.close()

        sendReconnectingStartEvent(cause)

        reconnectingHandler.startReconnectProcess(cause) { asyncReconnected ->
            if (asyncReconnected.succeeded()) {
                delegate = asyncReconnected.result()
                reconnectingInProgress = false
                sendReconnectingSucceededEvent()
            } else {
                sendReconnectingFailedEvent(asyncReconnected.cause())
            }
        }
    }

    private fun sendReconnectingStartEvent(cause: Throwable) {
        if (options.reconnectingNotifications) {
            vertx.eventBus().send(options.reconnectingStartNotificationAddress, cause.stackTraceToString())
        }
    }

    private fun sendReconnectingSucceededEvent() {
        if (options.reconnectingNotifications) {
            vertx.eventBus().send(options.reconnectingSucceededNotificationAddress, null)
        }
    }

    private fun sendReconnectingFailedEvent(cause: Throwable) {
        if (options.reconnectingNotifications) {
            vertx.eventBus().send(options.reconnectingFailedNotificationAddress, cause.stackTraceToString())
        }
    }

    private fun configureRedisConnectionFailureHandler(): RedisConnectionFailureHandler {
        return if (options.reconnect) {
            SimpleRedisReconnectingHandler(vertx, options)
        } else NoopRedisReconnectingHandler(options)
    }
}
