package ch.sourcemotion.vertx.redis.client.resilient.impl

import ch.sourcemotion.vertx.redis.client.resilient.AbstractVertxTest
import ch.sourcemotion.vertx.redis.client.resilient.RedisResilientOptions
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.redis.client.redisOptionsOf
import org.junit.jupiter.api.Test

internal class NoopRedisReconnectingHandlerTest : AbstractVertxTest() {

    @Test
    internal fun will_fail_immediately(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        NoopRedisReconnectingHandler(
            RedisResilientOptions(redisOptionsOf())
        ).startReconnectProcess(Exception("Test exception")) {
            testContext.verify {
                it.failed().shouldBeTrue()
                it.result().shouldBeNull()
                checkpoint.flag()
            }
        }
    }
}
