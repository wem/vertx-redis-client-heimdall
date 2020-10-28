package ch.sourcemotion.vertx.redis.client.heimdall

/**
 * General purpose Exception type, used by this library to signal usage and state exceptions.
 */
class RedisHeimdallException(val reason: Reason, message: String? = null, cause: Throwable? = null) : Exception(message, cause) {

    enum class Reason {
        /**
         * When a client is used the wrong way, e.g. send a non subscription related command over the subscription client.
         */
        UNSUPPORTED_ACTION,

        /**
         * Internal purposes. Should not get propagated to user.
         */
        INTERNAL,

        /**
         * On try to send / batch during reconnection process. (Fast-fail)
         */
        ACCESS_DURING_RECONNECT,

        /**
         * If the [RedisHeimdallOptions.maxReconnectAttempts] is not infinite and the attempts are reached.
         */
        MAX_ATTEMPTS_REACHED,

        /**
         * If the client did lost the connection and reconnect is disabled.
         */
        RECONNECT_DISABLED,

        /**
         * Most usual failure case. Thrown in the case of a connection issue.
         */
        CONNECTION_ISSUE,

        /**
         * If the user of the client tries to execute too many commands at once.
         */
        CLIENT_BUSY,

        /**
         * If the underlying client throws an unexpected exception type.
         */
        UNSPECIFIED
    }
}
