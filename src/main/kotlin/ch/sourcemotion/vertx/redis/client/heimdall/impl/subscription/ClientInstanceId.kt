package ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription

import io.vertx.core.shareddata.Shareable

/**
 * Id, assigned to each subscription client instance. Used to store subscribed channel names, so they can be re-subscribed
 * after a reconnect.
 */
@JvmInline
internal value class ClientInstanceId(private val value: String) : Shareable {
    override fun toString() = value
}
