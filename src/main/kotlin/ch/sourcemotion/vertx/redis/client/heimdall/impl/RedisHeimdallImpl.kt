package ch.sourcemotion.vertx.redis.client.heimdall.impl

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdall
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions
import ch.sourcemotion.vertx.redis.client.heimdall.impl.connection.RedisHeimdallConnection
import ch.sourcemotion.vertx.redis.client.heimdall.impl.reconnect.DefaultRedisReconnectProcess
import ch.sourcemotion.vertx.redis.client.heimdall.impl.reconnect.NoopRedisReconnectProcess
import ch.sourcemotion.vertx.redis.client.heimdall.impl.reconnect.RedisReconnectProcess
import io.vertx.core.*
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisConnection

internal open class RedisHeimdallImpl(
    protected val vertx: Vertx,
    private val options: RedisHeimdallOptions,
    private val postReconnectJobs: List<PostReconnectJob> = emptyList()
) : RedisHeimdall {

    private val logger = LoggerFactory.getLogger(RedisHeimdallImpl::class.java)

    protected var closed = false

    private var delegate: Redis = Redis.createClient(vertx, options)

    private var reconnectingInProgress = false

    private val reconnectingHandler: RedisReconnectProcess = configureRedisConnectionFailureHandler()

    init {
        vertx.orCreateContext.addCloseHook {
            close()
        }
    }

    protected fun skipConnectBecauseReconnecting(handler: Handler<AsyncResult<RedisConnection>>): Boolean {
        if (reconnectingInProgress) {
            handler.handle(
                Future.failedFuture(
                    RedisHeimdallException(
                        RedisHeimdallException.Reason.ACCESS_DURING_RECONNECT,
                        "Client is in reconnection process"
                    )
                )
            )
        }
        return reconnectingInProgress
    }

    override fun connect(handler: Handler<AsyncResult<RedisConnection>>): Redis {
        if (skipConnectBecauseReconnecting(handler)) {
            return this
        }

        delegate.connect { asyncConnection ->
            if (asyncConnection.succeeded()) {
                val connection =
                    getConnectionImplementation(
                        asyncConnection.result(),
                        this::handleConnectionFailure
                    ).initConnection()
                handler.handle(Future.succeededFuture(connection))
            } else {
                handler.handle(Future.failedFuture(asyncConnection.cause()))
                handleConnectionFailure(asyncConnection.cause())
            }
        }
        return this
    }

    override fun close() {
        if (closed.not()) {
            closed = true
            val closeResult = delegate.runCatching { close() }
            closeResult.exceptionOrNull()?.let { throw it }
        }
    }

    private fun handleConnectionFailure(cause: Throwable) {
        // We avoid reconnection process if the client was closed
        if (closed) {
            return
        }
        // Avoid multiple, parallel reconnection processes
        if (reconnectingInProgress) {
            logger.trace(
                "Avoid multiple reconnect processes for Redis client to server(s) ${options.endpointsToString()}",
                RedisHeimdallException(
                    RedisHeimdallException.Reason.INTERNAL,
                    "Multiple parallel reconnect processes"
                )
            )
            return
        }

        reconnectingInProgress = true

        val logMsg =
            "Connection(s) to Redis server(s) ${options.endpointsToString()} lost. Start to reconnect against provided configuration."
        // Debug level must be enabled for finer grained log
        if (logger.isDebugEnabled) {
            logger.warn(logMsg, cause)
        } else {
            logger.warn(logMsg)
        }

        cleanupBeforeReconnecting()

        sendReconnectingStartEvent(cause)

        reconnectingHandler.startReconnectProcess(cause) { asyncReconnected ->
            if (asyncReconnected.succeeded()) {
                delegate = asyncReconnected.result()
                reconnectingInProgress = false

                val jobs = postReconnectJobs.map { job -> Future.future<Unit> { job.execute(this, it) } }
                val jobsResult = CompositeFuture.all(jobs)
                jobsResult.onFailure {
                    logger.warn("At least one post reconnect job did fail. So we re-initiate reconnection process")
                    handleConnectionFailure(cause)
                }
                jobsResult.onSuccess { sendReconnectingSucceededEvent() }
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
            logger.debug("Send reconnecting succeeded event")
            vertx.eventBus().send(options.reconnectingSucceededNotificationAddress, null)
        }
    }

    private fun sendReconnectingFailedEvent(cause: Throwable) {
        if (options.reconnectingNotifications) {
            vertx.eventBus().send(options.reconnectingFailedNotificationAddress, cause.stackTraceToString())
        }
    }

    private fun configureRedisConnectionFailureHandler(): RedisReconnectProcess {
        return if (options.reconnect) {
            DefaultRedisReconnectProcess(vertx, options)
        } else NoopRedisReconnectProcess(options)
    }

    protected open fun getConnectionImplementation(
        delegateConnection: RedisConnection,
        connectionIssueHandler: Handler<Throwable>
    ) = RedisHeimdallConnection(delegateConnection, connectionIssueHandler)

    open fun cleanupBeforeReconnecting() {
        // Close delegate, so all resource get free
        delegate.close()
    }
}
