package ch.sourcemotion.vertx.redis.client.resilient.impl

import ch.sourcemotion.vertx.redis.client.resilient.AbstractRedisTest
import ch.sourcemotion.vertx.redis.client.resilient.RedisResilient
import ch.sourcemotion.vertx.redis.client.resilient.RedisResilientException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldNotBeBlank
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.redis.client.batchAwait
import io.vertx.kotlin.redis.client.connectAwait
import io.vertx.kotlin.redis.client.sendAwait
import io.vertx.redis.client.Command
import io.vertx.redis.client.RedisConnection
import io.vertx.redis.client.Request
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test

internal class ResilientRedisConnectionTest : AbstractRedisTest() {

    @Test
    internal fun send_successful(testContext: VertxTestContext) = testContext.async {
        val sut = RedisResilient.create(vertx, getDefaultRedisOptions()).connectAwait()
        sut.verifyConnectivityWithPingPongBySend()
    }

    @Test
    internal fun batch_successful(testContext: VertxTestContext) = testContext.async {
        val sut = RedisResilient.create(vertx, getDefaultRedisOptions()).connectAwait()
        sut.verifyConnectivityWithPingPongByBatch()
    }

    @Test
    internal fun send_notification_on_successful_reconnect(testContext: VertxTestContext) =
        testContext.async(2) { checkpoint ->
            val redisOptions = getDefaultRedisOptions()
            val client = RedisResilient.create(vertx, redisOptions)
            val sut = client.connectAwait()

            eventBus.consumer<String>(redisOptions.reconnectingStartNotificationAddress) {
                testContext.verify { it.body().shouldNotBeBlank() }
                checkpoint.flag()
            }

            eventBus.consumer<Unit>(redisOptions.reconnectingSucceededNotificationAddress) {
                testScope.launch {
                    client.connectAwait().verifyConnectivityWithPingPongBySend()
                    checkpoint.flag()
                }
            }

            eventBus.consumer<Unit>(redisOptions.reconnectingFailedNotificationAddress) {
                testContext.failNow(IllegalAccessException("On successful reconnect, the failed notification should not get send"))
            }

            downStreamTimeout()
            // Initiate reconnection process
            shouldThrow<RedisResilientException> { sut.verifyConnectivityWithPingPongBySend() }
            delay(redisOptions.reconnectInterval * 2)
            removeConnectionIssues()
        }

    @Test
    internal fun batch_notification_on_successful_reconnect(testContext: VertxTestContext) =
        testContext.async(2) { checkpoint ->
            val redisOptions = getDefaultRedisOptions()
            val client = RedisResilient.create(vertx, redisOptions)
            val sut = client.connectAwait()

            eventBus.consumer<String>(redisOptions.reconnectingStartNotificationAddress) {
                testContext.verify { it.body().shouldNotBeBlank() }
                checkpoint.flag()
            }

            eventBus.consumer<Unit>(redisOptions.reconnectingSucceededNotificationAddress) {
                testScope.launch {
                    client.connectAwait().verifyConnectivityWithPingPongByBatch()
                    checkpoint.flag()
                }
            }

            eventBus.consumer<Unit>(redisOptions.reconnectingFailedNotificationAddress) {
                testContext.failNow(IllegalAccessException("On successful reconnect, the failed notification should not get send"))
            }

            downStreamTimeout()
            // Initiate reconnection process
            shouldThrow<RedisResilientException> {
                sut.sendPingBatch()
            }
            delay(redisOptions.reconnectInterval * 2)
            removeConnectionIssues()
        }

    @Test
    internal fun send_notification_on_failed_reconnect(testContext: VertxTestContext) =
        testContext.async(2) { checkpoint ->
            val redisOptions = getDefaultRedisOptions().apply {
                reconnectInterval = 10
                maxReconnectAttempts = 1
            }
            val client = RedisResilient.create(vertx, redisOptions)
            val sut = client.connectAwait()

            eventBus.consumer<String>(redisOptions.reconnectingStartNotificationAddress) {
                testContext.verify { it.body().shouldNotBeBlank() }
                checkpoint.flag()
            }

            eventBus.consumer<String>(redisOptions.reconnectingFailedNotificationAddress) {
                testContext.verify { it.body().shouldNotBeBlank() }
                checkpoint.flag()
            }

            eventBus.consumer<Unit>(redisOptions.reconnectingSucceededNotificationAddress) {
                testContext.failNow(IllegalAccessException("On failed reconnect, the failed notification should not get send"))
            }

            downStreamTimeout()
            // Initiate reconnection process
            shouldThrow<RedisResilientException> { sut.sendAwait(Request.cmd(Command.PING)) }
        }

    @Test
    internal fun batch_notification_on_failed_reconnect(testContext: VertxTestContext) =
        testContext.async(2) { checkpoint ->
            val redisOptions = getDefaultRedisOptions().apply {
                reconnectInterval = 10
                maxReconnectAttempts = 1
            }
            val client = RedisResilient.create(vertx, redisOptions)
            val sut = client.connectAwait()

            eventBus.consumer<String>(redisOptions.reconnectingStartNotificationAddress) {
                testContext.verify { it.body().shouldNotBeBlank() }
                checkpoint.flag()
            }

            eventBus.consumer<String>(redisOptions.reconnectingFailedNotificationAddress) {
                testContext.verify { it.body().shouldNotBeBlank() }
                checkpoint.flag()
            }

            eventBus.consumer<Unit>(redisOptions.reconnectingSucceededNotificationAddress) {
                testContext.failNow(IllegalAccessException("On failed reconnect, the failed notification should not get send"))
            }

            downStreamTimeout()
            // Initiate reconnection process
            shouldThrow<RedisResilientException> { sut.sendPingBatch() }
        }

    private suspend fun RedisConnection.verifyConnectivityWithPingPongBySend() {
        val response = sendPing()
        response.shouldNotBeNull()
        response.shouldBePongResponse()
    }

    private suspend fun RedisConnection.verifyConnectivityWithPingPongByBatch(pingCount: Int = 2) {
        val responses = sendPingBatch(pingCount)
        responses.shouldNotBeNull()
        val availableResponses = responses.filterNotNull()
        availableResponses.shouldHaveSize(pingCount)
        availableResponses.forEach { it.shouldBePongResponse() }
    }

    private suspend fun RedisConnection.sendPing() = sendAwait(Request.cmd(Command.PING))

    private suspend fun RedisConnection.sendPingBatch(pingCount: Int = 2) =
        batchAwait(Array(pingCount) { Request.cmd(Command.PING) }.toList())
}
