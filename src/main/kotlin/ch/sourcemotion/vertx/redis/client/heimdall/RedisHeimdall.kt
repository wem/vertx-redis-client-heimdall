package ch.sourcemotion.vertx.redis.client.heimdall

import ch.sourcemotion.vertx.redis.client.heimdall.impl.RedisHeimdallImpl
import io.vertx.core.Vertx
import io.vertx.redis.client.Redis

/**
 * Redis client for common use. Additionally reconnection is handled automatically.
 * Please visit README for more detailed information.
 *
 * This extends the original Vert.x Redis client.
 * See [io.vertx.redis.client.Redis]
 */
interface RedisHeimdall : Redis, AutoCloseable {
    companion object {
        /**
         * Creates the common Redis Heimdall client.
         */
        @JvmStatic
        fun create(vertx: Vertx, options: RedisHeimdallOptions): RedisHeimdall = RedisHeimdallImpl(vertx, options)

        /**
         * Creates the light Redis Heimdall client. That client will use only a single connection to Redis.
         */
        @JvmStatic
        fun createLight(vertx: Vertx, options: RedisHeimdallOptions): RedisHeimdall = RedisHeimdallLight(vertx, options)
    }
}
