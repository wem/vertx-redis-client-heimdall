package ch.sourcemotion.vertx.redis.client.resilient

import io.vertx.redis.client.RedisOptions

class RedisResilientOptions(other: RedisOptions?) : RedisOptions(other) {

    companion object {
        const val DEFAULT_RECONNECT = true
        const val DEFAULT_RECONNECT_INTERVAL = 2000L
        const val DEFAULT_MAX_RECONNECT_TRIES = -1
        const val DEFAULT_RECONNECTING_NOTIFICATIONS = true
        const val DEFAULT_RECONNECTING_START_NOTIFICATION_ADDRESS = "/vertx/redis/resilient/reconnecting/start"
        const val DEFAULT_RECONNECTING_SUCCEEDED_NOTIFICATION_ADDRESS = "/vertx/redis/resilient/reconnecting/succeeded"
        const val DEFAULT_RECONNECTING_FAILED_NOTIFICATION_ADDRESS = "/vertx/redis/resilient/reconnecting/failed"
    }

    /**
     * Basic flag if this client should reconnect or not. If this flag is false, in fact this client extension will be
     * disabled.
     */
    var reconnect: Boolean = DEFAULT_RECONNECT

    /**
     * Interval used to try to reconnect to Redis service.
     * @TODO Backoff
     */
    var reconnectInterval = DEFAULT_RECONNECT_INTERVAL

    /**
     * Max number of attempts to reconnect they should be done before skip.
     */
    var maxReconnectAttempts = DEFAULT_MAX_RECONNECT_TRIES

    /**
     * If true, the client will send (not publish) a notification over the event bus on reconnecting for its state and appearance.
     */
    var reconnectingNotifications = DEFAULT_RECONNECTING_NOTIFICATIONS

    /**
     * The event body will contain the stacktrace of the underlying exception.
     */
    var reconnectingStartNotificationAddress = DEFAULT_RECONNECTING_START_NOTIFICATION_ADDRESS

    /**
     * The event body will be null.
     */
    var reconnectingSucceededNotificationAddress = DEFAULT_RECONNECTING_SUCCEEDED_NOTIFICATION_ADDRESS

    /**
     * The event body will contain the stacktrace of the underlying exception.
     */
    var reconnectingFailedNotificationAddress = DEFAULT_RECONNECTING_FAILED_NOTIFICATION_ADDRESS

    constructor() : this(RedisResilientOptions())

    fun endpointsToString() = endpoints.joinToString(",")
}
