package ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import ch.sourcemotion.vertx.redis.client.heimdall.impl.RedisHeimdallImpl
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.SubscriptionStore.Companion.createSubscriptionStore
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.connection.RedisHeimdallSubscriptionConnection
import ch.sourcemotion.vertx.redis.client.heimdall.subscription.RedisHeimdallSubscription
import ch.sourcemotion.vertx.redis.client.heimdall.subscription.RedisHeimdallSubscriptionOptions
import ch.sourcemotion.vertx.redis.client.heimdall.subscription.SubscriptionMessage
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.redis.client.*
import java.util.*

internal class RedisHeimdallSubscriptionImpl(
    vertx: Vertx,
    private val options: RedisHeimdallSubscriptionOptions,
    private val messageHandler: Handler<SubscriptionMessage>
) : RedisHeimdallImpl(vertx, options, listOf(SubscriptionPostReconnectJob)), RedisHeimdallSubscription {

    private companion object {
        private val logger = LoggerFactory.getLogger(RedisHeimdallSubscriptionImpl::class.java)
    }

    private val clientInstanceId = ClientInstanceId("${UUID.randomUUID()}")

    private val subscriptionStore = vertx.createSubscriptionStore(clientInstanceId)

    private var subscriptionConnection: RedisHeimdallSubscriptionConnection? = null

    /**
     * The start will fail on any failure, because there are several possible reasons and the consequences
     * could be really different. But to signal success and subscribe later would be no option.
     */
    fun start(handler: Handler<AsyncResult<RedisHeimdallSubscription>>) {
        if (options.channelNames.isEmpty() && options.channelPatterns.isEmpty()) {
            handler.handle(Future.succeededFuture(this))
            return
        }
        connect { asyncConnect ->
            if (asyncConnect.succeeded()) {
                val connection = asyncConnect.result()
                if (connection is RedisHeimdallSubscriptionConnection) {
                    connection.subscribeToChannelsAndPatterns(
                        options.channelNames,
                        options.channelPatterns
                    ) { asyncSubscriptions ->
                        if (asyncSubscriptions.succeeded()) {
                            handler.handle(Future.succeededFuture(this))
                        } else {
                            logger.warn(
                                "Unable to start Redis Heimdall subscription client (initial subscriptions)",
                                asyncConnect.cause()
                            )
                            val cause = RedisHeimdallException(Reason.UNABLE_TO_START, cause = asyncSubscriptions.cause())
                            handler.handle(Future.failedFuture(cause))
                        }
                    }
                } else {
                    logger.logWrongConnectionType(connection)
                    handler.handle(Future.failedFuture(RedisHeimdallException(Reason.UNABLE_TO_START, "Wrong connection type, please check logs")))
                }
            } else {
                logger.warn("Unable to start Redis Heimdall subscription client", asyncConnect.cause())
                val cause = RedisHeimdallException(Reason.UNABLE_TO_START, cause = asyncConnect.cause())
                handler.handle(Future.failedFuture(cause))
            }
        }
    }

    override fun addChannels(vararg channelNames: String, handler: Handler<AsyncResult<Response>>) =
        sendCommandWithStringArgs(channelNames, handler, Command.SUBSCRIBE)

    override suspend fun addChannelsAwait(vararg channelNames: String): RedisHeimdallSubscription {
        awaitResult<Response> { addChannels(*channelNames, handler = it) }
        return this
    }

    override fun removeChannels(
        vararg channelNames: String,
        handler: Handler<AsyncResult<Response>>
    ) = sendCommandWithStringArgs(channelNames, handler, Command.UNSUBSCRIBE)

    override suspend fun removeChannelsAwait(vararg channelNames: String): RedisHeimdallSubscription {
        awaitResult<Response> { removeChannels(*channelNames, handler = it) }
        return this
    }

    override fun addChannelPatterns(
        vararg channelPatterns: String,
        handler: Handler<AsyncResult<Response>>
    ) = sendCommandWithStringArgs(channelPatterns, handler, Command.PSUBSCRIBE)

    override suspend fun addChannelPatternsAwait(vararg channelPatterns: String): RedisHeimdallSubscription {
        awaitResult<Response> { addChannelPatterns(*channelPatterns, handler = it) }
        return this
    }

    override fun removeChannelPatterns(
        vararg channelPatterns: String,
        handler: Handler<AsyncResult<Response>>
    ) = sendCommandWithStringArgs(channelPatterns, handler, Command.PUNSUBSCRIBE)

    override suspend fun removeChannelPatternsAwait(vararg channelPatterns: String): RedisHeimdallSubscription {
        awaitResult<Response> { removeChannelPatterns(*channelPatterns, handler = it) }
        return this
    }

    private fun sendCommandWithStringArgs(
        arguments: Array<out String>,
        handler: Handler<AsyncResult<Response>>,
        command: Command
    ): RedisHeimdallSubscriptionImpl {
        val cmd = Request.cmd(command)
        arguments.forEach { channelName -> cmd.arg(channelName) }
        send(cmd, handler)
        return this
    }

    override fun send(command: Request, onSend: Handler<AsyncResult<Response>>): Redis {
        if (verifySubscriptionCommandOnly(command.command(), onSend)) {
            connect { asyncConnection ->
                if (asyncConnection.succeeded()) {
                    val connection = asyncConnection.result()
                    connection.send(command, onSend)
                    // We dont close the connection here, as it should NOT get back to pool
                } else {
                    onSend.handle(Future.failedFuture(asyncConnection.cause()))
                }
            }
        }
        return this
    }

    override fun batch(commands: MutableList<Request>, onSend: Handler<AsyncResult<List<Response>>>): Redis {
        if (verifySubscriptionCommandsOnly(commands.map { it.command() }, onSend)) {
            connect { asyncConnection ->
                if (asyncConnection.succeeded()) {
                    val connection = asyncConnection.result()
                    connection.batch(commands, onSend)
                    // We dont close the connection here, as it should NOT get back to pool
                } else {
                    onSend.handle(Future.failedFuture(asyncConnection.cause()))
                }
            }
        }
        return this
    }

    /**
     * In contrast to the default / inherited implementation, the subscription variant will use just a single connection.
     */
    override fun connect(handler: Handler<AsyncResult<RedisConnection>>): Redis {
        if (skipConnectBecauseReconnecting(handler)) {
            return this
        }

        // We use a single connection for subscriptions
        if (subscriptionConnection != null) {
            handler.handle(Future.succeededFuture(subscriptionConnection))
        } else {
            super.connect {
                if (it.succeeded()) {
                    val connection = it.result()
                    if (connection is RedisHeimdallSubscriptionConnection) {
                        subscriptionConnection = connection
                    } else {
                        logger.logWrongConnectionType(connection)
                    }
                }
                handler.handle(it)
            }
        }
        return this
    }

    override fun close() {
        if (closed.not()) {
            runCatching {
                releaseSubscriptionConnection()
            }
            runCatching { subscriptionStore.close() }
            super.close()
        }
    }

    override fun cleanupBeforeReconnecting() {
        // We release the connection before reconnecting, so afterwards a new connection could / should be used.
        releaseSubscriptionConnection()
        super.cleanupBeforeReconnecting()
    }

    private fun releaseSubscriptionConnection() {
        // Close the connection, so it will go back to pool and closed by delegated client.
        subscriptionConnection?.close()
        subscriptionConnection = null
    }

    override fun getConnectionImplementation(
        delegateConnection: RedisConnection,
        connectionIssueHandler: Handler<Throwable>
    ) = RedisHeimdallSubscriptionConnection(
        delegateConnection,
        connectionIssueHandler,
        subscriptionStore,
        this::onSubscriptionMessage
    )

    /**
     * Handling of subscription messages from Redis. Support for SUBSCRIBE AND PSUBSCRIBE resulting messages.
     */
    private fun onSubscriptionMessage(subscriptionResponse: Response) {
        val msg = if ("${subscriptionResponse[0]}" == "message") {
            val channel = "${subscriptionResponse[1]}"
            val message = "${subscriptionResponse[2]}"
            SubscriptionMessage(channel, message)
        } else {
            val channel = "${subscriptionResponse[2]}"
            val message = "${subscriptionResponse[3]}"
            SubscriptionMessage(channel, message)
        }
        messageHandler.handle(msg)
    }
}

internal fun Logger.logWrongConnectionType(connection: RedisConnection) {
    warn(
        "${connection::class.java.name} a subscription connection type. This is may a bug, " +
                "please check your configuration or create an issue on " +
                "https://github.com/wem/vertx-redis-client-heimdall/issues"
    )
}

fun verifySubscriptionCommandOnly(cmd: Command, handler: Handler<AsyncResult<Response>>) =
    if (RedisHeimdallSubscriptionConnection.supportedCommands.contains(cmd).not()) {
        handler.handle(
            Future.failedFuture(
                RedisHeimdallException(
                    Reason.UNSUPPORTED_ACTION,
                    "Command $cmd not supported in subscription mode"
                )
            )
        )
        false
    } else true

fun verifySubscriptionCommandsOnly(commands: List<Command>, handler: Handler<AsyncResult<List<Response>>>) =
    if (commands.any { !RedisHeimdallSubscriptionConnection.supportedCommands.contains(it) }) {
        handler.handle(
            Future.failedFuture(
                RedisHeimdallException(
                    Reason.UNSUPPORTED_ACTION,
                    "At least one Command $commands not supported in subscription mode"
                )
            )
        )
        false
    } else true
