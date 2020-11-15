package ch.sourcemotion.vertx.redis.client.heimdall

import io.vertx.redis.client.RedisOptions

open class RedisHeimdallOptions() {

    companion object {
        const val DEFAULT_RECONNECT = true
        const val DEFAULT_RECONNECT_INTERVAL = 2000L
        const val DEFAULT_MAX_RECONNECT_TRIES = -1

        const val DEFAULT_RECONNECTING_NOTIFICATIONS = true
        const val DEFAULT_RECONNECTING_START_NOTIFICATION_ADDRESS = "/vertx/redis/heimdall/reconnecting/start"
        const val DEFAULT_RECONNECTING_SUCCEEDED_NOTIFICATION_ADDRESS = "/vertx/redis/heimdall/reconnecting/succeeded"
        const val DEFAULT_RECONNECTING_FAILED_NOTIFICATION_ADDRESS = "/vertx/redis/heimdall/reconnecting/failed"
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

    var redisOptions: RedisOptions = RedisOptions()

    fun setReconnect(reconnect: Boolean) = this.apply { this.reconnect = reconnect }
    fun setReconnectInterval(reconnectInterval: Long) = this.apply { this.reconnectInterval = reconnectInterval }
    fun setMaxReconnectAttempts(maxReconnectAttempts: Int) =
        this.apply { this.maxReconnectAttempts = maxReconnectAttempts }

    fun setReconnectingNotifications(reconnectingNotifications: Boolean) =
        this.apply { this.reconnectingNotifications = reconnectingNotifications }

    fun setReconnectingStartNotificationAddress(reconnectingStartNotificationAddress: String) =
        this.apply { this.reconnectingStartNotificationAddress = reconnectingStartNotificationAddress }

    fun setReconnectingSucceededNotificationAddress(reconnectingSucceededNotificationAddress: String) =
        this.apply { this.reconnectingSucceededNotificationAddress = reconnectingSucceededNotificationAddress }

    fun setReconnectingFailedNotificationAddress(reconnectingFailedNotificationAddress: String) =
        this.apply { this.reconnectingFailedNotificationAddress = reconnectingFailedNotificationAddress }

    fun setRedisOptions(redisOptions: RedisOptions) =
        this.apply { this.redisOptions = redisOptions }

    constructor(redisOptions: RedisOptions) : this() {
        this.redisOptions = redisOptions
    }

    constructor(other: RedisHeimdallOptions) : this() {
        applyOther(other)
    }

    protected open fun applyOther(other: RedisHeimdallOptions) {
        reconnect = other.reconnect
        reconnectInterval = other.reconnectInterval
        maxReconnectAttempts = other.maxReconnectAttempts
        reconnectingNotifications = other.reconnectingNotifications
        reconnectingStartNotificationAddress = other.reconnectingStartNotificationAddress
        reconnectingSucceededNotificationAddress = other.reconnectingSucceededNotificationAddress
        reconnectingFailedNotificationAddress = other.reconnectingFailedNotificationAddress
        redisOptions = other.redisOptions
    }

    fun endpointsToString() = redisOptions.endpoints.joinToString(",")
}
