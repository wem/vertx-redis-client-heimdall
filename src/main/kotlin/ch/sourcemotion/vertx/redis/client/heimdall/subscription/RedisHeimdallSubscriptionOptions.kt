package ch.sourcemotion.vertx.redis.client.heimdall.subscription

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions
import io.vertx.redis.client.RedisOptions

class RedisHeimdallSubscriptionOptions @JvmOverloads constructor(other: RedisOptions = RedisOptions()) :
    RedisHeimdallOptions(other) {

    /**
     * Channel names they will get subscribed at client instantiation.
     * It's also possible to subscribe later, just use [RedisHeimdallSubscription.addChannels]
     */
    var channelNames = ArrayList<String>()

    /**
     * Channel name patterns they will get subscribed at client instantiation.
     * * It's also possible to subscribe later, just use [RedisHeimdallSubscription.addChannelPatterns]
     */
    var channelPatterns = ArrayList<String>()


    init {
        if (other is RedisHeimdallSubscriptionOptions) {
            channelNames = other.channelNames
            channelPatterns = other.channelPatterns
        }
    }

    fun addChannelNames(channelNames: List<String>) = this.apply { this.channelNames.addAll(channelNames) }
    fun addChannelPatterns(channelPatterns: List<String>) = this.apply { this.channelPatterns.addAll(channelPatterns) }
    fun addChannelNames(vararg channelNames : String) = this.apply { this.channelNames.addAll(channelNames) }
    fun addChannelPatterns(vararg channelPatterns: String) = this.apply { this.channelPatterns.addAll(channelPatterns) }
}
