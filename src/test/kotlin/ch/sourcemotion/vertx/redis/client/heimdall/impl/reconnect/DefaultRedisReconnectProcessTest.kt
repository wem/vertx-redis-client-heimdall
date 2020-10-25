package ch.sourcemotion.vertx.redis.client.heimdall.impl.reconnect

import ch.sourcemotion.vertx.redis.client.heimdall.testing.AbstractRedisTest
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.vertx.junit5.VertxTestContext
import io.vertx.redis.client.Redis
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test

internal class DefaultRedisReconnectProcessTest : AbstractRedisTest() {

    @Test
    internal fun reconnecting_success(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        // given
        val sut = DefaultRedisReconnectProcess(vertx, getDefaultRedisHeimdallOptions())

        // when & then
        sut.startReconnectProcess(Exception("Test exception")) {
            testContext.verify {
                it.succeeded().shouldBeTrue()
                it.result().shouldNotBeNull()
                it.result().shouldBeInstanceOf<Redis>()
                checkpoint.flag()
            }
        }
    }

    @Test
    internal fun reconnecting_fail(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        // given
        val sut = DefaultRedisReconnectProcess(vertx, getDefaultRedisHeimdallOptions().apply {
            this.reconnectInterval = 100
            this.maxReconnectAttempts = 2
        })

        // when
        downStreamTimeout()

        // then
        sut.startReconnectProcess(Exception("Test exception")) {
            testContext.verify {
                it.failed().shouldBeTrue()
                it.result().shouldBeNull()
                checkpoint.flag()
            }
        }
    }

    @Test
    internal fun reconnecting_after_some_attempts(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        // given
        val reconnectInterval = 100L
        val sut = DefaultRedisReconnectProcess(vertx, getDefaultRedisHeimdallOptions().apply {
            this.reconnectInterval = reconnectInterval
            this.maxReconnectAttempts = 5
        })

        // when
        downStreamTimeout()

        // then
        sut.startReconnectProcess(Exception("Test exception")) {
            testContext.verify {
                it.succeeded().shouldBeTrue()
                it.result().shouldNotBeNull()
                checkpoint.flag()
            }
        }

        // when (delayed)
        // We wait until some reconnection attempts are done before reconnect would be possible
        delay(reconnectInterval * 2)
        removeConnectionIssues()
    }
}
