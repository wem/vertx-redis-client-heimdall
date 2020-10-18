package ch.sourcemotion.vertx.redis.client.resilient

import ch.sourcemotion.vertx.redis.client.resilient.impl.RedisResilientImpl
import io.vertx.core.Vertx
import io.vertx.redis.client.Redis

interface RedisResilient {
    companion object {
        @JvmStatic
        fun create(vertx: Vertx, options: RedisResilientOptions): Redis = RedisResilientImpl(vertx, options)
    }
}
