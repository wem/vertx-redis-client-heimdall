package ch.sourcemotion.vertx.redis.client.heimdall.impl.reconnect

import ch.sourcemotion.vertx.redis.client.heimdall.testing.AbstractVertxTest
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.redis.client.redisOptionsOf
import org.junit.jupiter.api.Test

internal class NoopRedisReconnectProcessTest : AbstractVertxTest() {

    @Test
    internal fun will_fail_immediately(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        NoopRedisReconnectProcess(
            RedisHeimdallOptions(redisOptionsOf())
        ).startReconnectProcess(Exception("Test exception")) {
            testContext.verify {
                it.failed().shouldBeTrue()
                it.result().shouldBeNull()
                checkpoint.flag()
            }
        }
    }
}
