package ch.sourcemotion.vertx.redis.client.resilient

class RedisResilientException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
