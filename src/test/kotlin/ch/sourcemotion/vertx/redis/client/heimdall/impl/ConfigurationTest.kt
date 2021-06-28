package ch.sourcemotion.vertx.redis.client.heimdall.impl

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdall
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.testing.AbstractRedisTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.redis.client.sendAwait
import io.vertx.redis.client.Command
import io.vertx.redis.client.Request
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.junit.jupiter.api.RepeatedTest

internal class ConfigurationTest : AbstractRedisTest() {

    @RepeatedTest(5)
    internal fun max_pool_size(testContext: VertxTestContext) = testContext.async {
        val maxPoolSize = 31
        val maxPoolWaiting = 1
        val redisHeimdallOptions = getDefaultRedisHeimdallOptions().apply {
            redisOptions.setMaxPoolSize(maxPoolSize).maxPoolWaiting = maxPoolWaiting
        }
        val sut = RedisHeimdall.create(vertx, redisHeimdallOptions)

        coroutineScope {
            repeat(maxPoolSize + maxPoolWaiting - 1) {
                launch {
                    sut.send(Request.cmd(Command.GET).arg("key")).await()
                }
            }
        }
    }

    @RepeatedTest(5)
    internal fun max_pool_waiting(testContext: VertxTestContext) = testContext.async {
        val maxPoolSize = 1
        val maxPoolWaiting = 31
        val redisHeimdallOptions = getDefaultRedisHeimdallOptions().apply {
            redisOptions.setMaxPoolSize(maxPoolSize).maxPoolWaiting = maxPoolWaiting
        }
        val sut = RedisHeimdall.create(vertx, redisHeimdallOptions)

        coroutineScope {
            repeat(maxPoolSize + maxPoolWaiting - 1) {
                launch {
                    sut.send(Request.cmd(Command.GET).arg("key")).await()
                }
            }
        }
    }

    @RepeatedTest(5)
    internal fun max_pool_size_too_small(testContext: VertxTestContext) = testContext.async {
        val maxPoolSize = 31
        val maxPoolWaiting = 1
        val redisHeimdallOptions = getDefaultRedisHeimdallOptions().apply {
            redisOptions.setMaxPoolSize(maxPoolSize).maxPoolWaiting = maxPoolWaiting
        }
        val sut = RedisHeimdall.create(vertx, redisHeimdallOptions)

        shouldThrow<RedisHeimdallException> {
            coroutineScope {
                repeat((maxPoolSize + maxPoolWaiting) * 2) {
                    launch {
                        sut.send(Request.cmd(Command.GET).arg("key")).await()
                    }
                }
            }
        }.reason.shouldBe(RedisHeimdallException.Reason.CLIENT_BUSY)
    }

    @RepeatedTest(5)
    internal fun max_pool_waiting_too_small(testContext: VertxTestContext) = testContext.async {
        val maxPoolSize = 1
        val maxPoolWaiting = 31
        val redisHeimdallOptions = getDefaultRedisHeimdallOptions().apply {
            redisOptions.setMaxPoolSize(maxPoolSize).maxPoolWaiting = maxPoolWaiting
        }
        val sut = RedisHeimdall.create(vertx, redisHeimdallOptions)

        shouldThrow<RedisHeimdallException> {
            coroutineScope {
                repeat((maxPoolSize + maxPoolWaiting) * 2) {
                    launch {
                        sut.send(Request.cmd(Command.GET).arg("key")).await()
                    }
                }
            }
        }.reason.shouldBe(RedisHeimdallException.Reason.CLIENT_BUSY)
    }
}