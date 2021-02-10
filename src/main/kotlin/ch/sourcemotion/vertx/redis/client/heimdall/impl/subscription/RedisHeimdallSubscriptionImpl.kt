package ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import ch.sourcemotion.vertx.redis.client.heimdall.impl.RedisHeimdallImpl
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.SubscriptionStore.Companion.createSubscriptionStore
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.connection.RedisHeimdallSubscriptionConnection
import ch.sourcemotion.vertx.redis.client.heimdall.subscription.RedisHeimdallSubscription
import ch.sourcemotion.vertx.redis.client.heimdall.subscription.RedisHeimdallSubscriptionOptions
import ch.sourcemotion.vertx.redis.client.heimdall.subscription.SubscriptionMessage
import io.vertx.core.*
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.redis.client.Command
import io.vertx.redis.client.RedisConnection
import io.vertx.redis.client.Request
import io.vertx.redis.client.Response
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
    fun start(): Future<RedisHeimdallSubscription> {
        val promise = Promise.promise<RedisHeimdallSubscription>()
        if (options.channelNames.isEmpty() && options.channelPatterns.isEmpty()) {
            promise.complete(this)
            return promise.future()
        }
        connect()
            .onSuccess { connection ->
                if (connection is RedisHeimdallSubscriptionConnection) {
                    connection.subscribeToChannelsAndPatterns(
                        options.channelNames,
                        options.channelPatterns
                    ).onSuccess {
                        promise.complete(this)
                    }.onFailure {
                        logger.warn("Unable to start Redis Heimdall subscription client (initial subscriptions)", it)
                        promise.fail(RedisHeimdallException(Reason.UNABLE_TO_START, cause = it))
                    }
                } else {
                    logger.logWrongConnectionType(connection)
                    promise.fail(
                        RedisHeimdallException(
                            Reason.UNABLE_TO_START,
                            "Wrong connection type, please check logs"
                        )
                    )
                }
            }
            .onFailure { cause ->
                logger.warn("Unable to start Redis Heimdall subscription client", cause)
                promise.fail(RedisHeimdallException(Reason.UNABLE_TO_START, cause = cause))
            }
        return promise.future()
    }

    override fun addChannels(vararg channelNames: String) =
        sendCommandWithStringArgs(channelNames, Command.SUBSCRIBE)

    override suspend fun addChannelsAwait(vararg channelNames: String): RedisHeimdallSubscription {
        addChannels(*channelNames).await()
        return this
    }

    override fun removeChannels(
        vararg channelNames: String,
    ) = sendCommandWithStringArgs(channelNames, Command.UNSUBSCRIBE)

    override suspend fun removeChannelsAwait(vararg channelNames: String): RedisHeimdallSubscription {
        removeChannels(*channelNames).await()
        return this
    }

    override fun addChannelPatterns(
        vararg channelPatterns: String,
    ) = sendCommandWithStringArgs(channelPatterns, Command.PSUBSCRIBE)

    override suspend fun addChannelPatternsAwait(vararg channelPatterns: String): RedisHeimdallSubscription {
        addChannelPatterns(*channelPatterns).await()
        return this
    }

    override fun removeChannelPatterns(vararg channelPatterns: String) =
        sendCommandWithStringArgs(channelPatterns, Command.PUNSUBSCRIBE)

    override suspend fun removeChannelPatternsAwait(vararg channelPatterns: String): RedisHeimdallSubscription {
        removeChannelPatterns(*channelPatterns).await()
        return this
    }

    private fun sendCommandWithStringArgs(arguments: Array<out String>, command: Command): Future<Response> {
        val cmd = Request.cmd(command)
        arguments.forEach { channelName -> cmd.arg(channelName) }
        return send(cmd)
    }

    override fun send(command: Request): Future<Response> {
        val promise = Promise.promise<Response>()
        if (verifySubscriptionCommandOnly(command.command(), promise)) {
            connect()
                .onSuccess { it.send(command, promise) }
                .onFailure { promise.fail(it) }
        }
        return promise.future()
    }

    override fun batch(commands: List<Request>): Future<List<Response>> {
        val promise = Promise.promise<List<Response>>()
        if (verifySubscriptionCommandsOnly(commands.map { it.command() }, promise)) {
            connect()
                .onSuccess { it.batch(commands, promise) }
                .onFailure { promise.fail(it) }
        }
        return promise.future()
    }

    /**
     * In contrast to the default / inherited implementation, the subscription variant will use just a single connection.
     */
    override fun connect(): Future<RedisConnection> {
        val promise = Promise.promise<RedisConnection>()
        if (skipConnectBecauseReconnecting(promise)) {
            return promise.future()
        }

        // We use a single connection for subscriptions
        if (subscriptionConnection != null) {
            promise.complete(subscriptionConnection)
        } else {
            super.connect()
                .onSuccess { connection ->
                    if (connection is RedisHeimdallSubscriptionConnection) {
                        subscriptionConnection = connection
                    } else {
                        logger.logWrongConnectionType(connection)
                    }
                    promise.complete(connection)
                }
                .onFailure { promise.fail(it) }
        }
        return promise.future()
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

fun verifySubscriptionCommandOnly(cmd: Command, promise: Promise<Response>) =
    if (RedisHeimdallSubscriptionConnection.supportedCommands.contains(cmd).not()) {
        promise.tryFail(
            RedisHeimdallException(
                Reason.UNSUPPORTED_ACTION,
                "Command $cmd not supported in subscription mode"
            )
        )
        false
    } else true

fun verifySubscriptionCommandsOnly(commands: List<Command>, promise: Promise<List<Response>>) =
    if (commands.any { !RedisHeimdallSubscriptionConnection.supportedCommands.contains(it) }) {
        promise.tryFail(
            RedisHeimdallException(
                Reason.UNSUPPORTED_ACTION,
                "At least one Command $commands not supported in subscription mode"
            )
        )
        false
    } else true
