package ch.sourcemotion.vertx.redis.client.heimdall.impl

import io.vertx.core.Future
import io.vertx.redis.client.Redis

/**
 * Job that will get executed when client got reconnected. If a connection issue appears during this job, he needs
 * to invoke the reconnect process again.
 */
internal fun interface PostReconnectJob {
    fun execute(redis: Redis) : Future<Unit>
}
