package ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.connection

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import ch.sourcemotion.vertx.redis.client.heimdall.impl.connection.RedisHeimdallConnection
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.SubscriptionStore
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.verifySubscriptionCommandOnly
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.verifySubscriptionCommandsOnly
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.client.Command
import io.vertx.redis.client.RedisConnection
import io.vertx.redis.client.Request
import io.vertx.redis.client.Response

/**
 * Redis connection designed to be used for subscription purposes only.
 */
internal class RedisSubscriptionHeimdallConnection(
    delegate: RedisConnection,
    connectionIssueHandler: Handler<Throwable>,
    private val subscriptionStore: SubscriptionStore,
    private val messageHandler: Handler<Response>,
) : RedisHeimdallConnection(delegate, connectionIssueHandler) {

    companion object {
        private val logger = LoggerFactory.getLogger(RedisSubscriptionHeimdallConnection::class.java)
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

    fun subscribeAfterReconnect(handler: Handler<AsyncResult<Unit>>) {
        val channelNames = subscriptionStore.subscriptions()
        if (channelNames != null) {
            val cmd = Request.cmd(Command.SUBSCRIBE)
            channelNames.forEach { cmd.arg(it) }
            send(cmd) { asyncSubscriptionResponse ->
                if (asyncSubscriptionResponse.succeeded()) {
                    logger.info("Channels $channelNames registered after reconnect", asyncSubscriptionResponse.cause())
                    handler.handle(Future.succeededFuture())
                } else {
                    logger.warn("Unable to register channels $channelNames after reconnect", asyncSubscriptionResponse.cause())
                    handler.handle(Future.failedFuture(asyncSubscriptionResponse.cause()))
                }
            }
        } else handler.handle(Future.succeededFuture())
    }

    private fun handleSubscriptionMessage(response: Response) {
        when {
            "${response[0]}" == "subscribe" -> {
                subscriptionStore.addSubscription("${response[1]}")
            }
            "${response[0]}" == "unsubscribe" -> {
                subscriptionStore.removeSubscription("${response[1]}")
            }
            "${response[0]}" == "message" -> {
                messageHandler.handle(response)
            }
        }
    }
}
