package ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.connection

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import ch.sourcemotion.vertx.redis.client.heimdall.impl.connection.RedisHeimdallConnection
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.SubscriptionStore
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.verifySubscriptionCommandOnly
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.verifySubscriptionCommandsOnly
import io.vertx.core.*
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
        // Call super is mandatory here, as this implementation prevents setting the handler by interface.
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

    override fun send(command: Request, onSend: Handler<AsyncResult<Response>>): RedisConnection {
        if (verifySubscriptionCommandOnly(command.command(), onSend)) {
            super.send(command, onSend)
        }
        return this
    }

    override fun batch(commands: List<Request>, onSend: Handler<AsyncResult<List<Response>>>): RedisConnection {
        if (verifySubscriptionCommandsOnly(commands.map { it.command() }, onSend)) {
            super.batch(commands, onSend)
        }
        return this
    }

    fun subscribeToChannelsAndPatterns(
        channelNames: List<String>,
        channelPatterns: List<String>,
        handler: Handler<AsyncResult<Unit>>
    ) {
        val commandFutures = ArrayList<Future<Unit>>()
        if (channelNames.isNotEmpty()) {
            val promise = Promise.promise<Unit>()
            commandFutures.add(promise.future())
            val cmd = Request.cmd(Command.SUBSCRIBE)
            channelNames.addToCommandArgs(cmd)
            send(cmd) {
                if (it.succeeded()) {
                    logger.info("Channel(s) $channelNames subscribed")
                    promise.complete()
                } else {
                    logger.warn("Subscription to channel(s) $channelNames failed")
                    promise.fail(it.cause())
                }
            }
        }
        if (channelPatterns.isNotEmpty()) {
            val promise = Promise.promise<Unit>()
            commandFutures.add(promise.future())
            val cmd = Request.cmd(Command.PSUBSCRIBE)
            channelPatterns.addToCommandArgs(cmd)
            send(cmd) {
                if (it.succeeded()) {
                    logger.info("Channel pattern(s) $channelPatterns subscribed")
                    promise.complete()
                } else {
                    logger.warn("Subscription to channel pattern(s) $channelPatterns failed")
                    promise.fail(it.cause())
                }
            }
        }
        if (commandFutures.isNotEmpty()) {
            CompositeFuture.all(commandFutures.toList()).onSuccess {
                handler.handle(Future.succeededFuture(Unit))
            }.onFailure {
                handler.handle(Future.failedFuture(it))
            }
        } else {
            handler.handle(Future.succeededFuture(Unit))
        }
    }

    fun subscribeAfterReconnect(handler: Handler<AsyncResult<Unit>>) {
        val channelNames = subscriptionStore.subscriptions()?.toList() ?: emptyList()
        val patterns = subscriptionStore.patterns()?.toList() ?: emptyList()
        subscribeToChannelsAndPatterns(channelNames, patterns, handler)
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
