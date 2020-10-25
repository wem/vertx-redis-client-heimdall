package ch.sourcemotion.vertx.redis.client.heimdall

import ch.sourcemotion.vertx.redis.client.heimdall.impl.RedisHeimdallImpl
import io.vertx.core.Vertx
import io.vertx.redis.client.Redis

/**
 * Redis client for common use. Additionally reconnection is handled automatically.
 * Please visit README for more detailed information.
 */
interface RedisHeimdall : Redis, AutoCloseable {
    companion object {
        @JvmStatic
        fun create(vertx: Vertx, options: RedisHeimdallOptions): RedisHeimdall = RedisHeimdallImpl(vertx, options)
    }
}
