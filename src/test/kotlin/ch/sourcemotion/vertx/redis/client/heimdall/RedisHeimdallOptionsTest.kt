package ch.sourcemotion.vertx.redis.client.heimdall

import ch.sourcemotion.vertx.redis.client.heimdall.subscription.RedisHeimdallSubscriptionOptions
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.vertx.redis.client.RedisOptions
import org.junit.jupiter.api.Test

internal class RedisHeimdallOptionsTest {

    @Test
    internal fun options_value_propagation() {
        val expectedPoolSize = Int.MAX_VALUE
        val expectedReconnect = false
        val expectedReconnectInterval = Long.MAX_VALUE
        val expectedMaxReconnectAttempts = Int.MAX_VALUE
        val expectedReconnectingNotifications = false
        val expectedReconnectingStartNotificationAddress = "start-notification-addr"
        val expectedReconnectingSucceededNotificationAddress = "success-notification-addr"
        val expectedReconnectingFailedNotificationAddress = "failed-notification-addr"
        val expectedChannelName = "channel-name"
        val expectedChannelPattern = "channel-pattern"

        val baseOptions = RedisOptions().setMaxPoolSize(expectedPoolSize)
        val heimdallOptions = RedisHeimdallOptions(baseOptions)
            .setReconnect(expectedReconnect)
            .setReconnectInterval(expectedReconnectInterval)
            .setMaxReconnectAttempts(expectedMaxReconnectAttempts)
            .setReconnectingNotifications(expectedReconnectingNotifications)
            .setReconnectingStartNotificationAddress(expectedReconnectingStartNotificationAddress)
            .setReconnectingSucceededNotificationAddress(expectedReconnectingSucceededNotificationAddress)
            .setReconnectingFailedNotificationAddress(expectedReconnectingFailedNotificationAddress)

        val subscriptionOptions = RedisHeimdallSubscriptionOptions(heimdallOptions).addChannelNames(expectedChannelName)
            .addChannelPatterns(expectedChannelPattern)

        verifySubscriptionOptions(
            subscriptionOptions,
            expectedPoolSize,
            expectedReconnect,
            expectedReconnectInterval,
            expectedMaxReconnectAttempts,
            expectedReconnectingNotifications,
            expectedReconnectingStartNotificationAddress,
            expectedReconnectingSucceededNotificationAddress,
            expectedReconnectingFailedNotificationAddress,
            expectedChannelName,
            expectedChannelPattern
        )

        val subscriptionOptionsCopy = RedisHeimdallSubscriptionOptions(subscriptionOptions)
        verifySubscriptionOptions(
            subscriptionOptionsCopy,
            expectedPoolSize,
            expectedReconnect,
            expectedReconnectInterval,
            expectedMaxReconnectAttempts,
            expectedReconnectingNotifications,
            expectedReconnectingStartNotificationAddress,
            expectedReconnectingSucceededNotificationAddress,
            expectedReconnectingFailedNotificationAddress,
            expectedChannelName,
            expectedChannelPattern
        )
    }

    private fun verifySubscriptionOptions(
        subscriptionOptions: RedisHeimdallSubscriptionOptions,
        expectedPoolSize: Int,
        expectedReconnect: Boolean,
        expectedReconnectInterval: Long,
        expectedMaxReconnectAttempts: Int,
        expectedReconnectingNotifications: Boolean,
        expectedReconnectingStartNotificationAddress: String,
        expectedReconnectingSucceededNotificationAddress: String,
        expectedReconnectingFailedNotificationAddress: String,
        expectedChannelName: String,
        expectedChannelPattern: String
    ) {
        subscriptionOptions.maxPoolSize.shouldBe(expectedPoolSize)
        subscriptionOptions.reconnect.shouldBe(expectedReconnect)
        subscriptionOptions.reconnectInterval.shouldBe(expectedReconnectInterval)
        subscriptionOptions.maxReconnectAttempts.shouldBe(expectedMaxReconnectAttempts)
        subscriptionOptions.reconnectingNotifications.shouldBe(expectedReconnectingNotifications)
        subscriptionOptions.reconnectingStartNotificationAddress.shouldBe(expectedReconnectingStartNotificationAddress)
        subscriptionOptions.reconnectingSucceededNotificationAddress.shouldBe(
            expectedReconnectingSucceededNotificationAddress
        )
        subscriptionOptions.reconnectingFailedNotificationAddress.shouldBe(expectedReconnectingFailedNotificationAddress)
        subscriptionOptions.channelNames.shouldContainExactly(expectedChannelName)
        subscriptionOptions.channelPatterns.shouldContainExactly(expectedChannelPattern)
    }
}
