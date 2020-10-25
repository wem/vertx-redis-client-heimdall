package ch.sourcemotion.vertx.redis.client.heimdall.impl

import ch.sourcemotion.vertx.redis.client.heimdall.testing.AbstractRedisTest
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdall
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import ch.sourcemotion.vertx.redis.client.heimdall.testing.shouldBePongResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.redis.client.batchAwait
import io.vertx.kotlin.redis.client.sendAwait
import io.vertx.redis.client.Command
import io.vertx.redis.client.Redis
import io.vertx.redis.client.Request
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class RedisHeimdallImplTest : AbstractRedisTest() {

    @Test
    internal fun send_successful(testContext: VertxTestContext) = testContext.async {
        val sut = RedisHeimdall.create(vertx, getDefaultRedisHeimdallOptions())
        sut.verifyConnectivityWithPingPongBySend()
    }

    @Test
    internal fun batch_successful(testContext: VertxTestContext) = testContext.async {
        val sut = RedisHeimdall.create(vertx, getDefaultRedisHeimdallOptions())
        sut.verifyConnectivityWithPingPongByBatch()
    }

    @Test
    internal fun send_fast_fail_while_reconnect_in_progress(testContext: VertxTestContext) = testContext.async {
        val sut = RedisHeimdall.create(vertx, getDefaultRedisHeimdallOptions())

        downStreamTimeout()
        // Initiate reconnection process
        shouldThrow<RedisHeimdallException> { sut.sendPing() }

        val exceptionWhileReconnecting = shouldThrow<RedisHeimdallException> { sut.sendPing() }
        exceptionWhileReconnecting.reason.shouldBe(Reason.ACCESS_DURING_RECONNECT)
    }

    @Test
    internal fun batch_fast_fail_while_reconnect_in_progress(testContext: VertxTestContext) = testContext.async {
        // given
        val sut = RedisHeimdall.create(vertx, getDefaultRedisHeimdallOptions())

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
            val sut = RedisHeimdall.create(vertx, redisHeimdallOptions)

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
            val sut = RedisHeimdall.create(vertx, redisHeimdallOptions)

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
            val sut = RedisHeimdall.create(vertx, redisHeimdallOptions)

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
            val sut = RedisHeimdall.create(vertx, redisHeimdallOptions)

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
    internal fun error_handling_off_when_client_closed(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        // given
        val redisHeimdallOptions = getDefaultRedisHeimdallOptions()
        val sut = RedisHeimdall.create(vertx, redisHeimdallOptions)

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

    private suspend fun Redis.sendPing() = sendAwait(Request.cmd(Command.PING))

    private suspend fun Redis.sendPingBatch(pingCount: Int = 2) =
        batchAwait(Array(pingCount) { Request.cmd(Command.PING) }.toList())
}
