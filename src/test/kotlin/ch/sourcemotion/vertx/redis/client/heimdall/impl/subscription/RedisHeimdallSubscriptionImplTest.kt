package ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdall
import ch.sourcemotion.vertx.redis.client.heimdall.subscription.RedisHeimdallSubscription
import ch.sourcemotion.vertx.redis.client.heimdall.testing.AbstractRedisTest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.redis.client.sendAwait
import io.vertx.redis.client.Command
import io.vertx.redis.client.Request
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail


internal class RedisHeimdallSubscriptionImplTest : AbstractRedisTest() {

    @Test
    internal fun simple_pub_sub(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        // given
        val pubClient = RedisHeimdall.create(vertx, getDefaultRedisHeimdallOptions())
        val channelName = "test-channel"
        val message = "test-message"

        // then
        RedisHeimdallSubscription.createAwait(vertx, getDefaultRedisHeimdallOptions(), listOf(channelName)) { msg ->
            testContext.verify {
                msg.channel.shouldBe(channelName)
                msg.message.shouldBe(message)
            }
            checkpoint.flag()
        }

        // when
        pubClient.publishAndVerifyResponse(channelName, message)
    }

    @Test
    internal fun pub_sub_after_reconnect(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        // given
        val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
        val channelName = "test-channel"
        val message = "test-message"

        // then
        RedisHeimdallSubscription.createAwait(vertx, redisHeimdallOptions, listOf(channelName)) { msg ->
            testContext.verify {
                msg.channel.shouldBe(channelName)
                msg.message.shouldBe(message)
            }
            checkpoint.flag()
        }

        eventBus.consumer<Any>(redisHeimdallOptions.reconnectingSucceededNotificationAddress) {
            // when
            testScope.launch {
                val pubClient = RedisHeimdall.create(vertx, redisHeimdallOptions)
                pubClient.publishAndVerifyResponse(channelName, message)
            }
        }

        closeAndResumeConnection(redisHeimdallOptions)
    }

    @Test
    internal fun add_channel_after_start(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        // given
        val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
        val channelName = "test-channel"
        val message = "test-message"

        // then
        val sut = RedisHeimdallSubscription.createAwait(vertx, redisHeimdallOptions) { msg ->
            testContext.verify {
                msg.channel.shouldBe(channelName)
                msg.message.shouldBe(message)
            }
            checkpoint.flag()
        }

        // when
        sut.addChannelAwait(channelName)
        val pubClient = RedisHeimdall.create(vertx, redisHeimdallOptions)
        pubClient.publishAndVerifyResponse(channelName, message)
    }

    @Test
    internal fun remove_channel_after_start(testContext: VertxTestContext) = testContext.asyncDelayed(3) { checkpoint ->
        // given
        val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
        val channelName = "test-channel"
        val anotherChannelName = "another-test-channel"
        val message = "test-message"
        val anotherMessage = "another-test-message"
        val channelNames = listOf(channelName, anotherChannelName)

        // then
        val sut = RedisHeimdallSubscription.createAwait(vertx, redisHeimdallOptions, channelNames) { msg ->
            testContext.verify {
                channelNames.shouldContain(msg.channel)
                if (msg.channel == channelName) {
                    msg.message.shouldBe(message)
                } else {
                    msg.message.shouldBe(anotherMessage)
                }
            }
            checkpoint.flag()
        }

        // when
        val pubClient = RedisHeimdall.create(vertx, redisHeimdallOptions)
        // Verify messages on both channels get received
        pubClient.publishAndVerifyResponse(channelName, message)
        pubClient.publishAndVerifyResponse(anotherChannelName, anotherMessage)

        sut.removeChannelAwait(anotherChannelName)

        // Now only the "test-channel" should be received
        pubClient.publishAndVerifyResponse(channelName, message)
        pubClient.publishAndVerifyResponse(anotherChannelName, anotherMessage, 0)
    }

    @Test
    internal fun error_handling_off_when_client_closed(testContext: VertxTestContext) =
        testContext.async(1) { checkpoint ->
            // given
            val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
            // We starts the subscription client with a channel, so the connection will get established
            val channelName = "test-channel"
            val sut = RedisHeimdallSubscription.createAwait(vertx, redisHeimdallOptions, listOf(channelName)) { }

            // then NOT
            eventBus.consumer<String>(redisHeimdallOptions.reconnectingStartNotificationAddress) {
                fail("Connecting failure should not get propagated when client was closed before")
            }

            // when
            sut.close()
            downStreamTimeout()

            // We delay the end so the reconnecting start consumer would be called in async fashion
            launch {
                delay(2000)
                checkpoint.flag()
            }
        }


    private suspend fun RedisHeimdall.publishAndVerifyResponse(
        channelName: String,
        message: String,
        clientCountReceivedMsg: Int = 1
    ) {
        val response = publishMessage(channelName, message)
        response.shouldNotBeNull()
        response.toInteger().shouldBe(clientCountReceivedMsg)
    }

    private suspend fun RedisHeimdall.publishMessage(
        channelName: String,
        message: String
    ) = sendAwait(Request.cmd(Command.PUBLISH).arg(channelName).arg(message))
}
