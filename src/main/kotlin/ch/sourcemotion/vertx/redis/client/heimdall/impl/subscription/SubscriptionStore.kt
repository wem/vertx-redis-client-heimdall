package ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription

import io.vertx.core.Vertx
import io.vertx.core.logging.LoggerFactory
import io.vertx.core.shareddata.LocalMap
import io.vertx.core.shareddata.Shareable

internal class SubscriptionStore(
    private val delegate: LocalMap<String, MutableSet<String>>
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionStore::class.java)
        const val SUBSCRIPTION_LIST = "redis-heimdall-subscription-map"

        fun Vertx.createSubscriptionStore(clientInstanceId: ClientInstanceId) =
            SubscriptionStore(this.sharedData().getLocalMap("$clientInstanceId"))
    }

    fun subscriptions(): Set<String>? = delegate[SUBSCRIPTION_LIST]

    fun addSubscription(channelName: String) {
        val subscriptions = delegate[SUBSCRIPTION_LIST] ?: ShareableList()
        val added = subscriptions.add(channelName)
        delegate[SUBSCRIPTION_LIST] = subscriptions
        if (added) {
            logger.debug("Subscription channel \"$channelName\" stored")
        }
    }

    fun removeSubscription(channelName: String) = delegate[SUBSCRIPTION_LIST]?.remove(channelName).also {
        if (it == true) {
            logger.debug("Subscription channel \"$channelName\" removed from store")
        }
    }

    fun close() = delegate.close()
}

private class ShareableList<T> : HashSet<T>(), Shareable
