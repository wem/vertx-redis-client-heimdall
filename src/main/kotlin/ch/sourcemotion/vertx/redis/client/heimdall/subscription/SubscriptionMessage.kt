package ch.sourcemotion.vertx.redis.client.heimdall.subscription

/**
 * Message that will received by [RedisHeimdallSubscription]
 */
data class SubscriptionMessage(val channel: String, val message: String)
