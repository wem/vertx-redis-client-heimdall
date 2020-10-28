package ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions
import ch.sourcemotion.vertx.redis.client.heimdall.impl.RedisHeimdallImpl
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.SubscriptionStore.Companion.createSubscriptionStore
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.connection.RedisSubscriptionHeimdallConnection
import ch.sourcemotion.vertx.redis.client.heimdall.subscription.RedisHeimdallSubscription
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
    options: RedisHeimdallOptions,
    private val channelNames: List<String>,
    private val messageHandler: Handler<SubscriptionMessage>
) : RedisHeimdallImpl(vertx, options, listOf(SubscriptionPostReconnectJob)), RedisHeimdallSubscription {

    private companion object {
        private val logger = LoggerFactory.getLogger(RedisHeimdallSubscriptionImpl::class.java)
    }

    private val clientInstanceId = ClientInstanceId("${UUID.randomUUID()}")

    private val subscriptionStore = vertx.createSubscriptionStore(clientInstanceId)

    private var subscriptionConnection: RedisSubscriptionHeimdallConnection? = null

    fun start(handler: Handler<AsyncResult<RedisHeimdallSubscription>>) {
        if (channelNames.isNotEmpty()) {
            val subscribeCmd = Request.cmd(Command.SUBSCRIBE)
            channelNames.forEach { subscribeCmd.arg(it) }
            send(subscribeCmd) {
                if (it.succeeded()) {
                    logger.info("Initial channel(s) $channelNames subscribed")
                    handler.handle(Future.succeededFuture(this))
                } else {
                    handler.handle(Future.failedFuture(it.cause()))
                }
            }
        } else {
            logger.info("No channel(s) provided to subscribe")
            handler.handle(Future.succeededFuture(this))
        }
    }

    override fun addChannel(channelName: String, handler: Handler<AsyncResult<Response>>): RedisHeimdallSubscription {
        val cmd = Request.cmd(Command.SUBSCRIBE).arg(channelName)
        send(cmd, handler)
        return this
    }

    override suspend fun addChannelAwait(channelName: String): RedisHeimdallSubscription {
        awaitResult<Response> { addChannel(channelName, it) }
        return this
    }

    override fun removeChannel(
        channelName: String,
        handler: Handler<AsyncResult<Response>>
    ): RedisHeimdallSubscription {
        val cmd = Request.cmd(Command.UNSUBSCRIBE).arg(channelName)
        send(cmd, handler)
        return this
    }

    override suspend fun removeChannelAwait(channelName: String): RedisHeimdallSubscription {
        awaitResult<Response> { removeChannel(channelName, it) }
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
                    if (connection is RedisSubscriptionHeimdallConnection) {
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
            // We need to close the connection here, so it will go back to pool and closed by delegated client.
            subscriptionConnection?.close()
            subscriptionConnection = null
            super.close()
            subscriptionStore.close()
        }
    }

    override fun cleanupBeforeReconnecting() {
        // We null the connection before reconnecting, so afterwards a new connection could / should be used.
        subscriptionConnection?.close()
        subscriptionConnection = null
        super.cleanupBeforeReconnecting()
    }

    override fun getConnectionImplementation(
        delegateConnection: RedisConnection,
        connectionIssueHandler: Handler<Throwable>
    ) = RedisSubscriptionHeimdallConnection(delegateConnection, connectionIssueHandler, subscriptionStore) {
        val msg = SubscriptionMessage.createFromResponse(it)
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
    if (RedisSubscriptionHeimdallConnection.supportedCommands.contains(cmd).not()) {
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
    if (commands.any { !RedisSubscriptionHeimdallConnection.supportedCommands.contains(it) }) {
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
