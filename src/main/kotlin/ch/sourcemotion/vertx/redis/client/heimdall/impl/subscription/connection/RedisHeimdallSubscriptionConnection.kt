package ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.connection

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import ch.sourcemotion.vertx.redis.client.heimdall.impl.connection.RedisHeimdallConnection
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.SubscriptionStore
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.verifySubscriptionCommandOnly
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.verifySubscriptionCommandsOnly
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.client.Command
import io.vertx.redis.client.RedisConnection
import io.vertx.redis.client.Request
import io.vertx.redis.client.Response

/**
 * Redis connection designed to be used for subscription purposes only.
 */
internal class RedisHeimdallSubscriptionConnection(
    delegate: RedisConnection,
    connectionIssueHandler: Handler<Throwable>,
    private val subscriptionStore: SubscriptionStore,
    private val messageHandler: Handler<Response>,
) : RedisHeimdallConnection(delegate, connectionIssueHandler) {

    companion object {
        private val logger = LoggerFactory.getLogger(RedisHeimdallSubscriptionConnection::class.java)
        val supportedCommands = listOf(Command.SUBSCRIBE, Command.UNSUBSCRIBE, Command.PSUBSCRIBE, Command.PUNSUBSCRIBE)
    }

    override fun initConnection(): RedisHeimdallConnection {
        super.initConnection()
        // Call super instead of this reference is mandatory here, as this implementation prevents setting the handler by interface.
        super.handler(this::handleSubscriptionMessage)
        return this
    }

    override fun handler(handler: Handler<Response>): RedisConnection {
        // Avoid user bypasses the implementation
        throw RedisHeimdallException(
            Reason.UNSUPPORTED_ACTION,
            "In subscription mode, you should not register your own subscription handler, as it's handled by Redis Heimdall."
        )
    }

    override fun send(command: Request): Future<Response> {
        val promise = Promise.promise<Response>()
        return if (verifySubscriptionCommandOnly(command.command(), promise)) {
            super.send(command)
        } else promise.future() // Future already completed here verifySubscriptionCommandsOnly will complete it
    }

    override fun batch(commands: List<Request>): Future<List<Response>> {
        val promise = Promise.promise<List<Response>>()
        return if (verifySubscriptionCommandsOnly(commands.map { it.command() }, promise)) {
            super.batch(commands)
        } else promise.future() // Future already completed here verifySubscriptionCommandsOnly will complete it
    }

    fun subscribeToChannelsAndPatterns(
        channelNames: List<String>,
        channelPatterns: List<String>,
    ): Future<Unit> {
        val promise = Promise.promise<Unit>()
        val commandFutures = ArrayList<Future<*>>()
        if (channelNames.isNotEmpty()) {
            val cmd = Request.cmd(Command.SUBSCRIBE)
            channelNames.addToCommandArgs(cmd)
            val future = send(cmd)
                .onSuccess {
                    logger.info("Channel(s) $channelNames subscribed")
                }
                .onFailure {
                    logger.warn("Subscription to channel(s) $channelNames failed", it)
                }
            commandFutures.add(future)
        }
        if (channelPatterns.isNotEmpty()) {
            val cmd = Request.cmd(Command.PSUBSCRIBE)
            channelPatterns.addToCommandArgs(cmd)
            val future = send(cmd)
                .onSuccess {
                    logger.info("Channel pattern(s) $channelPatterns subscribed")
                }.onFailure {
                    logger.warn("Subscription to channel pattern(s) $channelPatterns failed", it)
                }
            commandFutures.add(future)
        }
        if (commandFutures.isNotEmpty()) {
            CompositeFuture.all(commandFutures.toList()).onSuccess {
                promise.complete()
            }.onFailure {
                promise.fail(it)
            }
        } else {
            promise.complete()
        }
        return promise.future()
    }

    fun subscribeAfterReconnect(): Future<Unit> {
        val channelNames = subscriptionStore.subscriptions()?.toList() ?: emptyList()
        val patterns = subscriptionStore.patterns()?.toList() ?: emptyList()
        return subscribeToChannelsAndPatterns(channelNames, patterns)
    }

    private fun handleSubscriptionMessage(response: Response) {
        when {
            "${response[0]}" == "subscribe" -> subscriptionStore.addSubscription("${response[1]}")
            "${response[0]}" == "unsubscribe" -> subscriptionStore.removeSubscription("${response[1]}")
            "${response[0]}" == "psubscribe" -> subscriptionStore.addPattern("${response[1]}")
            "${response[0]}" == "punsubscribe" -> subscriptionStore.removePattern("${response[1]}")
            "${response[0]}" == "message" -> messageHandler.handle(response)
            "${response[0]}" == "pmessage" -> messageHandler.handle(response)
            else -> logger.warn("Response / Message type from Redis not supported \"$response\"")
        }
    }
}

fun List<String>.addToCommandArgs(command: Request) = command.apply { forEach { command.arg(it) } }
