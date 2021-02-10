package ch.sourcemotion.vertx.redis.client.heimdall.subscription

import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.RedisHeimdallSubscriptionImpl
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
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
        ) = RedisHeimdallSubscriptionImpl(vertx, options, messageHandler).start()
    }

    fun addChannels(vararg channelNames: String): Future<Response>
    suspend fun addChannelsAwait(vararg channelNames: String): RedisHeimdallSubscription

    fun removeChannels(vararg channelNames: String): Future<Response>
    suspend fun removeChannelsAwait(vararg channelNames: String): RedisHeimdallSubscription

    fun addChannelPatterns(vararg channelPatterns: String): Future<Response>
    suspend fun addChannelPatternsAwait(vararg channelPatterns: String): RedisHeimdallSubscription

    fun removeChannelPatterns(vararg channelPatterns: String): Future<Response>
    suspend fun removeChannelPatternsAwait(vararg channelPatterns: String): RedisHeimdallSubscription

    override fun close()
}
