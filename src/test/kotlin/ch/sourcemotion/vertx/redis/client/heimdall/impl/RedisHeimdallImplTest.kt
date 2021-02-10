package ch.sourcemotion.vertx.redis.client.heimdall.impl

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdall
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import ch.sourcemotion.vertx.redis.client.heimdall.testing.AbstractRedisTest
import ch.sourcemotion.vertx.redis.client.heimdall.testing.shouldBePongResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.redis.client.batchAwait
import io.vertx.redis.client.Command
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisConnection
import io.vertx.redis.client.Request
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class RedisHeimdallImplTest : AbstractRedisTest() {

    @Test
    internal fun send_successful(testContext: VertxTestContext) = testContext.async {
        val sut = RedisHeimdall.create(vertx, getDefaultRedisHeimdallOptions()).markAsTestClient()
        sut.verifyConnectivityWithPingPongBySend()
    }

    @Test
    internal fun batch_successful(testContext: VertxTestContext) = testContext.async {
        val sut = RedisHeimdall.create(vertx, getDefaultRedisHeimdallOptions()).markAsTestClient()
        sut.verifyConnectivityWithPingPongByBatch()
    }

    @Test
    internal fun send_fast_fail_while_reconnect_in_progress(testContext: VertxTestContext) = testContext.async {
        val sut = RedisHeimdall.create(vertx, getDefaultRedisHeimdallOptions()).markAsTestClient()

        downStreamTimeout()
        // Initiate reconnection process
        shouldThrow<RedisHeimdallException> { sut.sendPing() }

        val exceptionWhileReconnecting = shouldThrow<RedisHeimdallException> { sut.sendPing() }
        exceptionWhileReconnecting.reason.shouldBe(Reason.ACCESS_DURING_RECONNECT)
    }

    @Test
    internal fun batch_fast_fail_while_reconnect_in_progress(testContext: VertxTestContext) = testContext.async {
        // given
        val sut = RedisHeimdall.create(vertx, getDefaultRedisHeimdallOptions()).markAsTestClient()

        // when
        downStreamTimeout()

        // then (Initiate reconnection process)
        shouldThrow<RedisHeimdallException> { sut.sendPingBatch() }

        // then
        val exceptionWhileReconnecting = shouldThrow<RedisHeimdallException> { sut.sendPingBatch() }
        exceptionWhileReconnecting.reason.shouldBe(Reason.ACCESS_DURING_RECONNECT)
    }

    @Test
    internal fun send_notification_on_successful_reconnect(testContext: VertxTestContext) =
        testContext.async(2) { checkpoint ->
            // given
            val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
            val sut = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()

            // then
            eventBus.consumer<String>(redisHeimdallOptions.reconnectingStartNotificationAddress) {
                testContext.verify { it.body().shouldNotBeBlank() }
                checkpoint.flag()
            }

            // then
            eventBus.consumer<Unit>(redisHeimdallOptions.reconnectingSucceededNotificationAddress) {
                testScope.launch {
                    sut.verifyConnectivityWithPingPongBySend()
                    checkpoint.flag()
                }
            }

            // then NOT
            eventBus.consumer<Unit>(redisHeimdallOptions.reconnectingFailedNotificationAddress) {
                testContext.failNow(IllegalAccessException("On successful reconnect, the failed notification should not get send"))
            }

            // when
            downStreamTimeout()
            // Initiate reconnection process
            shouldThrow<RedisHeimdallException> { sut.sendPing() }
            delay(redisHeimdallOptions.reconnectInterval * 2)
            removeConnectionIssues()
        }

    @Test
    internal fun batch_notification_on_successful_reconnect(testContext: VertxTestContext) =
        testContext.async(2) { checkpoint ->
            // given
            val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
            val sut = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()

            // then
            eventBus.consumer<String>(redisHeimdallOptions.reconnectingStartNotificationAddress) {
                testContext.verify { it.body().shouldNotBeBlank() }
                checkpoint.flag()
            }

            // then
            eventBus.consumer<Unit>(redisHeimdallOptions.reconnectingSucceededNotificationAddress) {
                testScope.launch {
                    sut.verifyConnectivityWithPingPongByBatch()
                    checkpoint.flag()
                }
            }

            // then NOT
            eventBus.consumer<Unit>(redisHeimdallOptions.reconnectingFailedNotificationAddress) {
                testContext.failNow(IllegalAccessException("On successful reconnect, the failed notification should not get send"))
            }

            // when
            downStreamTimeout()
            // Initiate reconnection process
            shouldThrow<RedisHeimdallException> { sut.sendPingBatch() }
            delay(redisHeimdallOptions.reconnectInterval * 2)
            removeConnectionIssues()
        }

    @Test
    internal fun send_notification_on_failed_reconnect(testContext: VertxTestContext) =
        testContext.async(2) { checkpoint ->
            // given
            val redisHeimdallOptions = getDefaultRedisHeimdallOptions().apply {
                reconnectInterval = 10
                maxReconnectAttempts = 1
            }
            val sut = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()

            // then
            eventBus.consumer<String>(redisHeimdallOptions.reconnectingStartNotificationAddress) {
                testContext.verify { it.body().shouldNotBeBlank() }
                checkpoint.flag()
            }

            // then
            eventBus.consumer<String>(redisHeimdallOptions.reconnectingFailedNotificationAddress) {
                testContext.verify { it.body().shouldNotBeBlank() }
                checkpoint.flag()
            }

            // then NOT
            eventBus.consumer<Unit>(redisHeimdallOptions.reconnectingSucceededNotificationAddress) {
                testContext.failNow(IllegalAccessException("On failed reconnect, the failed notification should not get send"))
            }

            // when
            downStreamTimeout()
            // Initiate reconnection process
            shouldThrow<RedisHeimdallException> { sut.sendPing() }
        }

    @Test
    internal fun batch_notification_on_failed_reconnect(testContext: VertxTestContext) =
        testContext.async(2) { checkpoint ->
            // given
            val redisHeimdallOptions = getDefaultRedisHeimdallOptions().apply {
                reconnectInterval = 10
                maxReconnectAttempts = 1
            }
            val sut = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()

            // then
            eventBus.consumer<String>(redisHeimdallOptions.reconnectingStartNotificationAddress) {
                testContext.verify { it.body().shouldNotBeBlank() }
                checkpoint.flag()
            }

            // then
            eventBus.consumer<String>(redisHeimdallOptions.reconnectingFailedNotificationAddress) {
                testContext.verify { it.body().shouldNotBeBlank() }
                checkpoint.flag()
            }

            // then NOT
            eventBus.consumer<Unit>(redisHeimdallOptions.reconnectingSucceededNotificationAddress) {
                testContext.failNow(IllegalAccessException("On failed reconnect, the failed notification should not get send"))
            }

            // when
            downStreamTimeout()
            // Initiate reconnection process
            shouldThrow<RedisHeimdallException> { sut.sendPingBatch() }
        }

    @Test
    internal fun error_handling_off_when_client_closed(testContext: VertxTestContext) =
        testContext.async(1) { checkpoint ->
            // given
            val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
            val sut = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()

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

    @Test
    internal fun too_many_commands_at_once(testContext: VertxTestContext) = testContext.async {
        val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
        val redisOptions = redisHeimdallOptions.redisOptions
        val sut = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()

        // The must be able to execute a number of commands according to max pool size
        coroutineScope {
            repeat(redisOptions.maxPoolSize) {
                launch {
                    sut.verifyConnectivityWithPingPongBySend()
                }
            }
        }

        // On too many parallel commands, some of them must fail because the client becomes busy.
        var failedCommandCount = 0
        coroutineScope {
            repeat(redisOptions.maxPoolSize * 10) {
                launch {
                    runCatching { sut.sendPing() }
                        .onFailure {
                            testContext.verify {
                                val heimdallException = it.shouldBeInstanceOf<RedisHeimdallException>()
                                heimdallException.reason.shouldBe(Reason.CLIENT_BUSY)
                            }
                            failedCommandCount++
                        }
                }
            }
        }

        failedCommandCount.shouldBeGreaterThan(0)

        // The must be able to execute a number of commands according to max pool size again
        coroutineScope {
            repeat(redisOptions.maxPoolSize) {
                launch {
                    sut.verifyConnectivityWithPingPongBySend()
                }
            }
        }
    }

    @Test
//    @RepeatedTest(10)
    internal fun close_connection_while_send_commands_in_flight(testContext: VertxTestContext) {
        val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
        val redisOptions = redisHeimdallOptions.redisOptions
        val commandCount = redisOptions.maxPoolSize

        testContext.async(commandCount) { checkpoint ->
            val expectedExceptionReasons = listOf(Reason.CONNECTION_ISSUE, Reason.ACCESS_DURING_RECONNECT)
            val sut = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()

            coroutineScope {
                repeat(commandCount) { commandNbr ->
                    if (commandNbr == commandCount / 2) {
                        closeConnection()
                    }
                    launch {
                        runCatching { sut.verifyConnectivityWithPingPongBySend() }
                            .onFailure { cause ->
                                testContext.verify {
                                    val heimdallException = cause.shouldBeInstanceOf<RedisHeimdallException>()
                                    expectedExceptionReasons.shouldContain(heimdallException.reason)
                                }
                            }
                        checkpoint.flag()
                    }
                }
            }
        }
    }

    @Test
//    @RepeatedTest(10)
    internal fun close_connection_while_batches_in_flight(testContext: VertxTestContext) {
        val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
        val commandCount = redisHeimdallOptions.redisOptions.maxPoolSize

        testContext.async(commandCount) { checkpoint ->
            val expectedExceptionReasons = listOf(Reason.CONNECTION_ISSUE, Reason.ACCESS_DURING_RECONNECT)
            val sut = RedisHeimdall.create(vertx, redisHeimdallOptions).markAsTestClient()

            coroutineScope {
                repeat(commandCount) { commandNbr ->
                    val commandDelay = (commandNbr * 10).toLong()
                    delay(commandDelay)
                    launch {
                        runCatching { sut.verifyConnectivityWithPingPongByBatch() }
                            .onFailure { cause ->
                                testContext.verify {
                                    val heimdallException = cause.shouldBeInstanceOf<RedisHeimdallException>()
                                    expectedExceptionReasons.shouldContain(heimdallException.reason)
                                }
                            }
                        checkpoint.flag()
                    }
                    if (commandNbr == commandCount / 2) {
                        closeConnection()
                    }
                }
            }
        }
    }

    @Test
    internal fun exception_handling_connect_failure(testContext: VertxTestContext) =
        testContext.async(1) { checkpoint ->

            val redisHeimdallOptions = getDefaultRedisHeimdallOptions()

            eventBus.consumer<Unit>(redisHeimdallOptions.reconnectingStartNotificationAddress) {
                checkpoint.flag()
            }

            val expectedRootCause = Exception("Test cause")

            val failingDelegate = mockk<Redis> {
                every { connect() } answers {
                    Future.failedFuture(expectedRootCause)
                }
            }

            // Little bit hacky, but to make the delegate accessible from outside would also be bad.
            val sut = RedisHeimdallImpl(vertx, redisHeimdallOptions)
            val delegateField = sut::class.java.getDeclaredField("delegate")
            delegateField.isAccessible = true
            delegateField.set(sut, failingDelegate)

            sut.connect(testContext.failing { cause ->
                cause.shouldBeInstanceOf<RedisHeimdallException>()
                cause.reason.shouldBe(Reason.CONNECTION_ISSUE)
                cause.cause.shouldBe(expectedRootCause)
            })
        }

    @Test
    internal fun failing_post_reconnect_job_will_start_reconnect_again(testContext: VertxTestContext) =
        testContext.async(2) { checkpoint ->
            val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
            eventBus.consumer<Unit>(redisHeimdallOptions.reconnectingStartNotificationAddress) {
                checkpoint.flag()
            }

            val sut = RedisHeimdallImpl(
                vertx,
                redisHeimdallOptions,
                listOf(PostReconnectJob { Future.failedFuture(Exception("Test exception")) })
            )

            sut.verifyConnectivityWithPingPongBySend()

            closeAndResumeConnection(redisHeimdallOptions)
        }

    private suspend fun Redis.verifyConnectivityWithPingPongBySend() {
        val response = sendPing()
        response.shouldNotBeNull()
        response.shouldBePongResponse()
    }

    private suspend fun Redis.verifyConnectivityWithPingPongByBatch(pingCount: Int = 2) {
        val responses = sendPingBatch(pingCount)
        responses.shouldNotBeNull()
        val availableResponses = responses.filterNotNull()
        availableResponses.shouldHaveSize(pingCount)
        availableResponses.forEach { it.shouldBePongResponse() }
    }

    private suspend fun Redis.sendPing() = send(Request.cmd(Command.PING)).await()

    private suspend fun Redis.sendPingBatch(pingCount: Int = 2) =
        batch(Array(pingCount) { Request.cmd(Command.PING) }.toList()).await()
}
