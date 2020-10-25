package ch.sourcemotion.vertx.redis.client.heimdall.subscription

import io.vertx.redis.client.Response

data class SubscriptionMessage(val channel: String, val message: String) {
    internal companion object {
        fun createFromResponse(response: Response) = SubscriptionMessage("${response[1]}", "${response[2]}")
    }
}
