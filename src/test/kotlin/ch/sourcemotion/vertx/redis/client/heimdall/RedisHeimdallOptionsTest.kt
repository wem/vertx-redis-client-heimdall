package ch.sourcemotion.vertx.redis.client.heimdall

import ch.sourcemotion.vertx.redis.client.heimdall.subscription.RedisHeimdallSubscriptionOptions
import ch.sourcemotion.vertx.redis.client.heimdall.testing.AbstractVertxTest
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.deployVerticleAwait
import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.kotlin.core.eventbus.requestAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.redis.client.RedisOptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class RedisHeimdallOptionsTest : AbstractVertxTest() {

    @BeforeEach
    internal fun setUpJsonForKotlin() {
        DatabindCodec.mapper().registerKotlinModule()
    }

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
        subscriptionOptions.redisOptions.maxPoolSize.shouldBe(expectedPoolSize)
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

    @Test
    internal fun redis_heimdall_options_as_verticle_options(testContext: VertxTestContext) = testContext.async {
        vertx.deployVerticleAwait(
            VerticleHasRedisHeimdallOptions::class.java.name,
            deploymentOptionsOf(
                config = JsonObject.mapFrom(
                    VerticleHasRedisHeimdallOptions.Options(
                        RedisHeimdallOptions().setReconnect(
                            false
                        )
                    )
                )
            )
        )
        eventBus.requestAwait<Boolean>(VerticleHasRedisHeimdallOptions.RECONNECT_VALUE_QUERY_ADDR, null).body()
            .shouldBeFalse()
    }

    @Test
    internal fun redis_heimdall_subscription_options_as_verticle_options(testContext: VertxTestContext) =
        testContext.async {
            val channelName = "some-channel"
            vertx.deployVerticleAwait(
                VerticleHasRedisHeimdallSubscriptionOptions::class.java.name,
                deploymentOptionsOf(
                    config = JsonObject.mapFrom(
                        VerticleHasRedisHeimdallSubscriptionOptions.Options(
                            RedisHeimdallSubscriptionOptions().addChannelNames(channelName)
                        )
                    )
                )
            )
            val replyMsg = eventBus.requestAwait<String>(
                VerticleHasRedisHeimdallSubscriptionOptions.CHANNEL_NAME_VALUE_QUERY_ADDR,
                null
            )
            replyMsg.body().shouldBe(channelName)
        }
}

class VerticleHasRedisHeimdallOptions : CoroutineVerticle() {

    companion object {
        const val RECONNECT_VALUE_QUERY_ADDR = "query-reconnect"
    }

    private lateinit var options: Options

    override suspend fun start() {
        options = config.mapTo(Options::class.java)
        vertx.eventBus().consumer(RECONNECT_VALUE_QUERY_ADDR, this::onReconnectValueQuery)
    }

    private fun onReconnectValueQuery(msg: Message<Unit>) {
        msg.reply(options.redisOptions.reconnect)
    }

    data class Options(
        val redisOptions: RedisHeimdallOptions
    )
}

class VerticleHasRedisHeimdallSubscriptionOptions : CoroutineVerticle() {

    companion object {
        const val CHANNEL_NAME_VALUE_QUERY_ADDR = "query-channel-name"
    }

    private lateinit var options: Options

    override suspend fun start() {
        options = config.mapTo(Options::class.java)
        vertx.eventBus().consumer(CHANNEL_NAME_VALUE_QUERY_ADDR, this::onChannelNameQuery)
    }

    private fun onChannelNameQuery(msg: Message<Unit>) {
        msg.reply(options.redisOptions.channelNames.first())
    }

    data class Options(
        val redisOptions: RedisHeimdallSubscriptionOptions
    )
}
