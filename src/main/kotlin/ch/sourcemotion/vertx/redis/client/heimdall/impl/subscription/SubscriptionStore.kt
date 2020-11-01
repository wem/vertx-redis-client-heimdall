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
        const val CHANNEL_LIST = "redis-heimdall-channel-list"
        const val PATTERN_LIST = "redis-heimdall-pattern-list"

        fun Vertx.createSubscriptionStore(clientInstanceId: ClientInstanceId) =
            SubscriptionStore(this.sharedData().getLocalMap("$clientInstanceId"))
    }

    fun subscriptions(): Set<String>? = delegate[CHANNEL_LIST]
    fun patterns(): Set<String>? = delegate[PATTERN_LIST]

    fun addSubscription(channelName: String) {
        val subscriptions = delegate[CHANNEL_LIST] ?: ShareableList()
        val added = subscriptions.add(channelName)
        delegate[CHANNEL_LIST] = subscriptions
        if (added) {
            logger.debug("Subscription channel name \"$channelName\" stored")
        }
    }

    fun addPattern(channelPattern: String) {
        val patterns = delegate[PATTERN_LIST] ?: ShareableList()
        val added = patterns.add(channelPattern)
        delegate[PATTERN_LIST] = patterns
        if (added) {
            logger.debug("Subscription channel pattern \"$channelPattern\" stored")
        }
    }

    fun removeSubscription(channelName: String) = delegate[CHANNEL_LIST]?.remove(channelName).also {
        if (it == true) {
            logger.debug("Subscription channel \"$channelName\" removed from store")
        }
    }

    fun removePattern(channelPattern: String) = delegate[PATTERN_LIST]?.remove(channelPattern).also {
        if (it == true) {
            logger.debug("Subscription channel pattern \"$channelPattern\" removed from store")
        }
    }

    fun close() = delegate.close()
}

private class ShareableList<T> : HashSet<T>(), Shareable
