package ch.sourcemotion.vertx.redis.client.heimdall.impl

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdall
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions
import ch.sourcemotion.vertx.redis.client.heimdall.impl.connection.RedisHeimdallConnection
import ch.sourcemotion.vertx.redis.client.heimdall.impl.reconnect.DefaultRedisReconnectProcess
import ch.sourcemotion.vertx.redis.client.heimdall.impl.reconnect.NoopRedisReconnectProcess
import ch.sourcemotion.vertx.redis.client.heimdall.impl.reconnect.RedisReconnectProcess
import io.vertx.core.*
import io.vertx.core.http.ConnectionPoolTooBusyException
import io.vertx.core.impl.ContextInternal
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisConnection
import io.vertx.redis.client.Request
import io.vertx.redis.client.Response

internal open class RedisHeimdallImpl(
    protected val vertx: Vertx,
    private val options: RedisHeimdallOptions,
    private val postReconnectJobs: List<PostReconnectJob> = emptyList()
) : RedisHeimdall {

    private val logger = LoggerFactory.getLogger(RedisHeimdallImpl::class.java)

    protected var closed = false

    private var delegate: Redis = Redis.createClient(vertx, options.redisOptions)

    var reconnectingInProgress = false
        private set

    private val reconnectingHandler: RedisReconnectProcess = configureRedisConnectionFailureHandler()

    init {
        val ctx = vertx.orCreateContext
        if (ctx is ContextInternal) {
            ctx.addCloseHook { closingPromise ->
                runCatching { close() }
                    .onSuccess { closingPromise.complete() }
                    .onFailure { closingPromise.fail(it) }
            }
        }
    }

    protected fun skipConnectBecauseReconnecting(promise: Promise<RedisConnection>): Boolean {
        if (reconnectingInProgress) {
            promise.fail(RedisHeimdallException(Reason.ACCESS_DURING_RECONNECT, "Client is in reconnection process"))
        }
        return reconnectingInProgress
    }

    override fun connect(): Future<RedisConnection> {
        val promise = Promise.promise<RedisConnection>()
        if (skipConnectBecauseReconnecting(promise)) {
            return promise.future()
        }

        delegate.connect()
            .onSuccess { connection ->
                val connectionImpl =
                    getConnectionImplementation(connection, this::handleConnectionFailure).initConnection()
                promise.complete(connectionImpl)
            }
            .onFailure { cause ->
                if (cause is ConnectionPoolTooBusyException) {
                    promise.fail(
                        RedisHeimdallException(
                            Reason.CLIENT_BUSY,
                            "Too many commands to Redis at once, please use a rate limiting or increase RedisOptions.maxPoolSize",
                            cause
                        )
                    )
                } else {
                    handleConnectionFailure(cause)
                    promise.fail(RedisHeimdallException(Reason.CONNECTION_ISSUE, cause = cause))
                }
            }
        return promise.future()
    }

    override fun send(command: Request): Future<Response> {
        val promise = Promise.promise<Response>()

        if (command.command().isPubSub) {
            promise.fail(
                RedisHeimdallException(
                    Reason.UNSUPPORTED_ACTION,
                    "Please use Redis Heimdall subscription client for PubSub"
                )
            )
            return promise.future()
        }

        connect()
            .onSuccess { conn: RedisConnection ->
                conn.send(command)
                    .onComplete { send: AsyncResult<Response> ->
                        runCatching { conn.close() }
                        promise.handle(send)
                    }
            }
            .onFailure { promise.fail(it) }

        return promise.future()
    }

    override fun batch(commands: List<Request>): Future<List<Response>> {
        val promise = Promise.promise<List<Response>>()

        for (req in commands) {
            if (req.command().isPubSub) {
                // mixing pubSub cannot be used on a one-shot operation
                promise.fail("PubSub command in connection-less batch not allowed")
                return promise.future()
            }
        }

        connect()
            .onSuccess { conn: RedisConnection ->
                conn.batch(commands)
                    .onComplete { send: AsyncResult<List<Response>> ->
                        runCatching { conn.close() }
                        promise.handle(send)
                    }
            }
            .onFailure { promise.fail(it) }

        return promise.future()
    }

    override fun close() {
        if (closed.not()) {
            closed = true
            delegate.close()
        }
    }

    private fun handleConnectionFailure(cause: Throwable) {
        // We avoid reconnection process if the client was closed
        if (closed) {
            logger.debug("Already closed client will not reconnect")
            return
        }
        // Avoid multiple, parallel reconnection processes
        if (reconnectingInProgress) {
            logger.trace("Avoid multiple reconnect processes for Redis client to server(s) ${options.endpointsToString()}")
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

        reconnectingHandler.startReconnectProcess(cause)
            .onSuccess {
                // We close the previous connection after successful reconnect, because during reconnect there can be still tasks on the fly.
                runCatching {
                    delegate.close()// Close previous delegate, so all resource get free
                }
                delegate = it
                reconnectingInProgress = false

                val jobs = postReconnectJobs.map { job -> job.execute(this) }
                val jobsResult = CompositeFuture.all(jobs)
                jobsResult.onSuccess { sendReconnectingSucceededEvent() }
                jobsResult.onFailure {
                    logger.warn("At least one post reconnect job did fail. So we re-initiate reconnection process")
                    handleConnectionFailure(cause)
                }
            }
            .onFailure { sendReconnectingFailedEvent(it) }
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

    open fun cleanupBeforeReconnecting() {}
}
