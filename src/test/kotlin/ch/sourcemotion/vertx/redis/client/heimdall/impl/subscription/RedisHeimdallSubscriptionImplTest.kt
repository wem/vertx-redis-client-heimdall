package ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdall
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.SubscriptionStore.Companion.createSubscriptionStore
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.connection.RedisHeimdallSubscriptionConnection
import ch.sourcemotion.vertx.redis.client.heimdall.subscription.RedisHeimdallSubscription
import ch.sourcemotion.vertx.redis.client.heimdall.subscription.SubscriptionMessage
import ch.sourcemotion.vertx.redis.client.heimdall.testing.AbstractRedisTest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.redis.client.sendAwait
import io.vertx.redis.client.Command
import io.vertx.redis.client.RedisConnection
import io.vertx.redis.client.Request
import io.vertx.redis.client.Response
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail


internal class RedisHeimdallSubscriptionImplTest : AbstractRedisTest() {

    companion object {
        const val TEST_CHANNEL = "test-channel"
        const val TEST_CHANNEL_PATTERN = "test-*"
        const val TEST_CHANNEL_MSG = "test-channel-msg"
        const val ANOTHER_TEST_CHANNEL = "another-$TEST_CHANNEL"
        const val ANOTHER_TEST_CHANNEL_PATTERN = "another-test-*"
        const val ANOTHER_TEST_CHANNEL_MSG = "another-$TEST_CHANNEL_MSG"
    }

    @Test
    internal fun simple_pub_sub(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        // given
        val pubClient = RedisHeimdall.create(vertx, getDefaultRedisHeimdallOptions()).markAsTestClient()
        val subscriptionClientOptions = getDefaultRedisHeimdallSubscriptionOptions().addChannelNames(TEST_CHANNEL)

        // then
        RedisHeimdallSubscription.createAwait(vertx, subscriptionClientOptions) { msg ->
            testContext.verify {
                verifyCommonTestChannel(msg)
            }
            checkpoint.flag()
        }.markAsTestClient()

        // when
        pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG)
    }

    @Test
    internal fun simple_pub_sub_by_pattern(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        // given
        val pubClient = RedisHeimdall.create(vertx, getDefaultRedisHeimdallOptions()).markAsTestClient()
        val subscriptionClientOptions = getDefaultRedisHeimdallSubscriptionOptions().addChannelPatterns(
            TEST_CHANNEL_PATTERN
        )

        // then
        RedisHeimdallSubscription.createAwait(vertx, subscriptionClientOptions) { msg ->
            testContext.verify {
                verifyCommonTestChannel(msg)
            }
            checkpoint.flag()
        }.markAsTestClient()

        // when
        pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG)
    }

    @Test
    internal fun simple_pub_sub_by_channel_name_and_pattern(testContext: VertxTestContext) =
        testContext.async(2) { checkpoint ->
            // given
            val pubClient = RedisHeimdall.create(vertx, getDefaultRedisHeimdallOptions()).markAsTestClient()
            val subscriptionClientOptions = getDefaultRedisHeimdallSubscriptionOptions().addChannelNames(TEST_CHANNEL)
                .addChannelPatterns(ANOTHER_TEST_CHANNEL_PATTERN)

            // then
            RedisHeimdallSubscription.createAwait(vertx, subscriptionClientOptions) { msg ->
                testContext.verify {
                    verifyMessagesOnBothChannels(msg)
                    checkpoint.flag()
                }
            }.markAsTestClient()

            // when
            pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG)
            pubClient.publishAndVerifyResponse(ANOTHER_TEST_CHANNEL, ANOTHER_TEST_CHANNEL_MSG)
        }

    @Test
    internal fun pub_sub_after_reconnect(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        // given
        val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
        val subscriptionClientOptions = getDefaultRedisHeimdallSubscriptionOptions().addChannelNames(TEST_CHANNEL)

        // then
        RedisHeimdallSubscription.createAwait(vertx, subscriptionClientOptions) { msg ->
            testContext.verify {
                verifyCommonTestChannel(msg)
            }
            checkpoint.flag()
        }.markAsTestClient()

        eventBus.consumer<Any>(redisHeimdallOptions.reconnectingSucceededNotificationAddress) {
            // when
            testScope.launch {
                val pubClient = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()
                pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG)
            }
        }

        closeAndResumeConnection(redisHeimdallOptions)
    }

    @Test
    internal fun pub_sub_by_pattern_after_reconnect(testContext: VertxTestContext) =
        testContext.async(1) { checkpoint ->
            // given
            val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
            val subscriptionClientOptions =
                getDefaultRedisHeimdallSubscriptionOptions().addChannelPatterns(TEST_CHANNEL_PATTERN)

            // then
            RedisHeimdallSubscription.createAwait(vertx, subscriptionClientOptions) { msg ->
                testContext.verify {
                    verifyCommonTestChannel(msg)
                }
                checkpoint.flag()
            }.markAsTestClient()

            eventBus.consumer<Any>(redisHeimdallOptions.reconnectingSucceededNotificationAddress) {
                // when
                testScope.launch {
                    val pubClient = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()
                    pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG)
                }
            }

            closeAndResumeConnection(redisHeimdallOptions)
        }

    @Test
    internal fun pub_sub_by_channel_name_and_pattern_after_reconnect(testContext: VertxTestContext) =
        testContext.async(2) { checkpoint ->
            // given
            val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
            val subscriptionClientOptions = getDefaultRedisHeimdallSubscriptionOptions()
                .addChannelNames(TEST_CHANNEL).addChannelPatterns(ANOTHER_TEST_CHANNEL_PATTERN)

            // then
            RedisHeimdallSubscription.createAwait(vertx, subscriptionClientOptions) { msg ->
                testContext.verify {
                    verifyMessagesOnBothChannels(msg)
                    checkpoint.flag()
                }
            }.markAsTestClient()

            eventBus.consumer<Any>(redisHeimdallOptions.reconnectingSucceededNotificationAddress) {
                // when
                testScope.launch {
                    val pubClient = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()
                    pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG)
                    pubClient.publishAndVerifyResponse(ANOTHER_TEST_CHANNEL, ANOTHER_TEST_CHANNEL_MSG)
                }
            }

            closeAndResumeConnection(redisHeimdallOptions)
        }

    @Test
    internal fun add_channel_after_start(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        // given
        val redisHeimdallOptions = getDefaultRedisHeimdallSubscriptionOptions()

        // then
        val sut = RedisHeimdallSubscription.createAwait(vertx, redisHeimdallOptions) { msg ->
            testContext.verify {
                verifyCommonTestChannel(msg)
            }
            checkpoint.flag()
        }.markAsTestClient()

        // when
        sut.addChannelsAwait(TEST_CHANNEL)
        val pubClient = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()
        pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG)
    }

    @Test
    internal fun add_pattern_after_start(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        // given
        val redisHeimdallOptions = getDefaultRedisHeimdallSubscriptionOptions()

        // then
        val sut = RedisHeimdallSubscription.createAwait(vertx, redisHeimdallOptions) { msg ->
            testContext.verify {
                verifyCommonTestChannel(msg)
            }
            checkpoint.flag()
        }.markAsTestClient()

        // when
        sut.addChannelPatternsAwait(TEST_CHANNEL_PATTERN)
        val pubClient = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()
        pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG)
    }

    @Test
    internal fun add_channel_name_and_pattern_after_start(testContext: VertxTestContext) =
        testContext.async(2) { checkpoint ->
            // given
            val redisHeimdallOptions = getDefaultRedisHeimdallSubscriptionOptions()

            // then
            val sut = RedisHeimdallSubscription.createAwait(vertx, redisHeimdallOptions) { msg ->
                testContext.verify {
                    verifyMessagesOnBothChannels(msg)
                }
                checkpoint.flag()
            }.markAsTestClient()

            // when
            sut.addChannelsAwait(TEST_CHANNEL)
            sut.addChannelPatternsAwait(ANOTHER_TEST_CHANNEL_PATTERN)
            val pubClient = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()
            pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG)
            pubClient.publishAndVerifyResponse(ANOTHER_TEST_CHANNEL, ANOTHER_TEST_CHANNEL_MSG)
        }

    @Test
    internal fun remove_channel_after_start(testContext: VertxTestContext) = testContext.asyncDelayed(3) { checkpoint ->
        // given
        val channelNames = listOf(TEST_CHANNEL, ANOTHER_TEST_CHANNEL)
        val redisHeimdallOptions = getDefaultRedisHeimdallSubscriptionOptions().addChannelNames(channelNames)

        // then
        val sut = RedisHeimdallSubscription.createAwait(vertx, redisHeimdallOptions) { msg ->
            testContext.verify {
                channelNames.shouldContain(msg.channel)
                verifyMessagesOnBothChannels(msg)
            }
            checkpoint.flag()
        }.markAsTestClient()

        // when
        val pubClient = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()
        // Verify messages on both channels get received
        pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG)
        pubClient.publishAndVerifyResponse(ANOTHER_TEST_CHANNEL, ANOTHER_TEST_CHANNEL_MSG)

        sut.removeChannelsAwait(ANOTHER_TEST_CHANNEL)

        // Now only the "test-channel" should be received
        pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG)
        pubClient.publishAndVerifyResponse(ANOTHER_TEST_CHANNEL, ANOTHER_TEST_CHANNEL_MSG, 0)
    }

    @Test
    internal fun remove_pattern_after_start(testContext: VertxTestContext) = testContext.asyncDelayed(3) { checkpoint ->
        // given
        val redisHeimdallOptions =
            getDefaultRedisHeimdallSubscriptionOptions().addChannelPatterns(
                TEST_CHANNEL_PATTERN,
                ANOTHER_TEST_CHANNEL_PATTERN
            )

        // then
        val sut = RedisHeimdallSubscription.createAwait(vertx, redisHeimdallOptions) { msg ->
            testContext.verify {
                verifyMessagesOnBothChannels(msg)
            }
            checkpoint.flag()
        }.markAsTestClient()

        // when
        val pubClient = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()
        // Verify messages on both channels get received
        pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG)
        pubClient.publishAndVerifyResponse(ANOTHER_TEST_CHANNEL, ANOTHER_TEST_CHANNEL_MSG)

        sut.removeChannelPatternsAwait(ANOTHER_TEST_CHANNEL_PATTERN)

        // Now only the "test-channel" should be received
        pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG)
        pubClient.publishAndVerifyResponse(ANOTHER_TEST_CHANNEL, ANOTHER_TEST_CHANNEL_MSG, 0)
    }

    @Test
    internal fun remove_channel_name_and_pattern_after_start(testContext: VertxTestContext) =
        testContext.asyncDelayed(2) { checkpoint ->
            // given
            val redisHeimdallOptions =
                getDefaultRedisHeimdallSubscriptionOptions()
                    .addChannelNames(TEST_CHANNEL)
                    .addChannelPatterns(ANOTHER_TEST_CHANNEL_PATTERN)

            // then
            val sut = RedisHeimdallSubscription.createAwait(vertx, redisHeimdallOptions) { msg ->
                testContext.verify {
                    verifyMessagesOnBothChannels(msg)
                }
                checkpoint.flag()
            }.markAsTestClient()

            // when
            val pubClient = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()
            // Verify messages on both channels get received
            pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG)
            pubClient.publishAndVerifyResponse(ANOTHER_TEST_CHANNEL, ANOTHER_TEST_CHANNEL_MSG)

            sut.removeChannelsAwait(TEST_CHANNEL)
            sut.removeChannelPatternsAwait(ANOTHER_TEST_CHANNEL_PATTERN)

            // Now only the "test-channel" should be received
            pubClient.publishAndVerifyResponse(TEST_CHANNEL, TEST_CHANNEL_MSG, 0)
            pubClient.publishAndVerifyResponse(ANOTHER_TEST_CHANNEL, ANOTHER_TEST_CHANNEL_MSG, 0)
        }

    @Test
    internal fun start_without_subscriptions_success_even_on_connection_issue(testContext: VertxTestContext) =
        testContext.async {
            closeConnection()
            RedisHeimdallSubscription.createAwait(vertx, getDefaultRedisHeimdallSubscriptionOptions()) {}
        }

    @Test
    internal fun start_with_fail_on_connect_issue(testContext: VertxTestContext) =
        testContext.async(1) { checkpoint ->

            val expectedCause = Exception("connect issue")

            val subscriptionClient = RedisHeimdallSubscriptionImpl(vertx, getDefaultRedisHeimdallSubscriptionOptions().addChannelNames(TEST_CHANNEL).addChannelPatterns(
                ANOTHER_TEST_CHANNEL_PATTERN)) {}
            val sut = spyk(subscriptionClient) {
                every { connect(any()) } answers {
                    val handler = this.arg<Handler<AsyncResult<RedisConnection>>>(0)
                    handler.handle(Future.failedFuture(expectedCause))
                    this@spyk
                }
            }

            sut.start(testContext.failing {
                val exception = it.shouldBeInstanceOf<RedisHeimdallException>()
                exception.reason.shouldBe(Reason.UNABLE_TO_START)
                exception.cause.shouldBe(expectedCause)
                checkpoint.flag()
            })
        }

    @Test
    internal fun start_with_fail_on_send(testContext: VertxTestContext) =
        testContext.async(1) { checkpoint ->

            val expectedCause = Exception("connect issue")

            val subscriptionConnection = spyk(RedisHeimdallSubscriptionConnection(mockk(), {}, vertx.createSubscriptionStore(
                ClientInstanceId("ed852aae-314f-4791-80db-fe079d437c82")
            )){}) {
                every { send(any(), any()) } answers  {
                    val handler = this.arg<Handler<AsyncResult<RedisConnection>>>(1)
                    handler.handle(Future.failedFuture(expectedCause))
                    this@spyk
                }
            }

            val subscriptionClient = RedisHeimdallSubscriptionImpl(vertx, getDefaultRedisHeimdallSubscriptionOptions().addChannelNames(TEST_CHANNEL).addChannelPatterns(
                ANOTHER_TEST_CHANNEL_PATTERN)) {}
            val sut = spyk(subscriptionClient) {
                every { connect(any()) } answers {
                    val handler = this.arg<Handler<AsyncResult<RedisConnection>>>(0)
                    handler.handle(Future.succeededFuture(subscriptionConnection))
                    this@spyk
                }
            }

            sut.start(testContext.failing {
                val exception = it.shouldBeInstanceOf<RedisHeimdallException>()
                exception.reason.shouldBe(Reason.UNABLE_TO_START)
                exception.cause.shouldBe(expectedCause)
                checkpoint.flag()
            })
        }

    @Test
    internal fun start_with_channel_pattern_fail_on_send(testContext: VertxTestContext) =
        testContext.async(1) { checkpoint ->

            val expectedCause = Exception("connect issue")
            val response = mockk<Response>()

            val subscriptionConnection = spyk(RedisHeimdallSubscriptionConnection(mockk(), {}, vertx.createSubscriptionStore(
                ClientInstanceId("ed852aae-314f-4791-80db-fe079d437c82")
            )){}) {
                every { send(any(), any()) } answers  {
                    val request = this.arg<Request>(0)
                    val handler = this.arg<Handler<AsyncResult<Response>>>(1)
                    if (request.command() == Command.SUBSCRIBE) {
                        handler.handle(Future.succeededFuture(response))
                    } else {
                        handler.handle(Future.failedFuture(expectedCause))
                    }
                    this@spyk
                }
            }

            val subscriptionClient = RedisHeimdallSubscriptionImpl(vertx, getDefaultRedisHeimdallSubscriptionOptions().addChannelNames(TEST_CHANNEL).addChannelPatterns(
                ANOTHER_TEST_CHANNEL_PATTERN)) {}
            val sut = spyk(subscriptionClient) {
                every { connect(any()) } answers {
                    val handler = this.arg<Handler<AsyncResult<RedisConnection>>>(0)
                    handler.handle(Future.succeededFuture(subscriptionConnection))
                    this@spyk
                }
            }

            sut.start(testContext.failing {
                val exception = it.shouldBeInstanceOf<RedisHeimdallException>()
                exception.reason.shouldBe(Reason.UNABLE_TO_START)
                exception.cause.shouldBe(expectedCause)
                checkpoint.flag()
            })
        }

    @Test
    internal fun start_with_fail_on_wrong_connection_type(testContext: VertxTestContext) =
        testContext.async {

            val wrongConnection = mockk<RedisConnection> {}

            val sut = RedisHeimdallSubscriptionImpl(vertx, getDefaultRedisHeimdallSubscriptionOptions().addChannelNames(TEST_CHANNEL).addChannelPatterns(
                ANOTHER_TEST_CHANNEL_PATTERN)) {}
            spyk(sut) {
                every { connect(any()) } answers {
                    val handler = this.arg<Handler<AsyncResult<RedisConnection>>>(0)
                    handler.handle(Future.succeededFuture(wrongConnection))
                    this@spyk
                }
            }

            sut.start(testContext.failing {
                val exception = it.shouldBeInstanceOf<RedisHeimdallException>()
                exception.reason.shouldBe(Reason.UNABLE_TO_START)
                exception.cause.shouldBeNull()
            })
        }

    @Test
    internal fun error_handling_off_when_client_closed(testContext: VertxTestContext) =
        testContext.async(1) { checkpoint ->
            // given
            // We starts the subscription client with a channel, so the connection will get established
            val redisHeimdallOptions = getDefaultRedisHeimdallSubscriptionOptions().addChannelNames(TEST_CHANNEL)
            val sut = RedisHeimdallSubscription.createAwait(vertx, redisHeimdallOptions) {}.markAsTestClient()

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

    private fun verifyMessagesOnBothChannels(msg: SubscriptionMessage) {
        if (msg.channel == TEST_CHANNEL) {
            msg.message.shouldBe(TEST_CHANNEL_MSG)
        } else {
            msg.channel.shouldBe(ANOTHER_TEST_CHANNEL)
            msg.message.shouldBe(ANOTHER_TEST_CHANNEL_MSG)
        }
    }

    private fun verifyCommonTestChannel(msg: SubscriptionMessage) {
        msg.channel.shouldBe(TEST_CHANNEL)
        msg.message.shouldBe(TEST_CHANNEL_MSG)
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
