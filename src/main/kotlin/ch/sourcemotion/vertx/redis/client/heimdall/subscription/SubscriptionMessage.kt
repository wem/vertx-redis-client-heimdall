package ch.sourcemotion.vertx.redis.client.heimdall.subscription

data class SubscriptionMessage(val channel: String, val message: String)
