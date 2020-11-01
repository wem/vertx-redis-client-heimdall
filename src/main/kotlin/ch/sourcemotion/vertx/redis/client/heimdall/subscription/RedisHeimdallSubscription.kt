package ch.sourcemotion.vertx.redis.client.heimdall.subscription

import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.RedisHeimdallSubscriptionImpl
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.redis.client.Response

/**
 * Specialized variant of a Redis client only for subscription purposes. It implements the Vert.x Redis usage according to
 * https://vertx.io/docs/vertx-redis-client/java/#_pub_sub_mode.
 *
 * Additionally, reconnection is handled automatically, inclusive re-register of all known channels.
 *
 * Please visit README for more detailed information.
 */
interface RedisHeimdallSubscription : AutoCloseable {
    companion object {
        @JvmStatic
        fun create(
            vertx: Vertx,
            options: RedisHeimdallSubscriptionOptions,
            messageHandler: Handler<SubscriptionMessage>,
            handler: Handler<AsyncResult<RedisHeimdallSubscription>>
        ) = RedisHeimdallSubscriptionImpl(vertx, options, messageHandler).start(handler)

        /**
         * Kotlin variant of create function.
         */
        suspend fun createAwait(
            vertx: Vertx,
            options: RedisHeimdallSubscriptionOptions,
            messageHandler: Handler<SubscriptionMessage>
        ): RedisHeimdallSubscription = awaitResult { create(vertx, options, messageHandler, it) }
    }

    fun addChannels(vararg channelNames: String, handler: Handler<AsyncResult<Response>>): RedisHeimdallSubscription

    suspend fun addChannelsAwait(vararg channelNames: String): RedisHeimdallSubscription
    fun removeChannels(vararg channelNames: String, handler: Handler<AsyncResult<Response>>): RedisHeimdallSubscription

    suspend fun removeChannelsAwait(vararg channelNames: String): RedisHeimdallSubscription

    fun addChannelPatterns(
        vararg channelPatterns: String,
        handler: Handler<AsyncResult<Response>>
    ): RedisHeimdallSubscription

    suspend fun addChannelPatternsAwait(vararg channelPatterns: String): RedisHeimdallSubscription

    fun removeChannelPatterns(
        vararg channelPatterns: String,
        handler: Handler<AsyncResult<Response>>
    ): RedisHeimdallSubscription

    suspend fun removeChannelPatternsAwait(vararg channelPatterns: String): RedisHeimdallSubscription

    override fun close()
}
