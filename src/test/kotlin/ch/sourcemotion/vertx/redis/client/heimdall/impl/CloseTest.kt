package ch.sourcemotion.vertx.redis.client.heimdall.impl

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdall
import ch.sourcemotion.vertx.redis.client.heimdall.subscription.RedisHeimdallSubscription
import ch.sourcemotion.vertx.redis.client.heimdall.testing.AbstractRedisTest
import io.kotest.matchers.shouldBe
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.deployVerticleAwait
import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.redis.client.Command
import io.vertx.redis.client.Request
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.LazyThreadSafetyMode.NONE

internal class CloseTest : AbstractRedisTest() {

    @Timeout(5, timeUnit = TimeUnit.SECONDS)
    @Test
    internal fun common_client_not_hangs_on_close(testContext: VertxTestContext) = testContext.async {
        vertx.deployVerticle({
            object : CoroutineVerticle() {
                private val client by lazy(NONE) { RedisHeimdall.create(vertx, getDefaultRedisHeimdallOptions()) }
                override suspend fun start() {
                    client.send(Request.cmd(Command.PING), testContext.succeeding {
                        testContext.verify {
                            "$it".shouldBe("PONG")
                        }
                    })
                }
            }
        }, deploymentOptionsOf()).await()
    }

    @Timeout(5, timeUnit = TimeUnit.SECONDS)
    @Test
    internal fun subscription_client_not_hangs_on_close(testContext: VertxTestContext) = testContext.async {
        vertx.deployVerticle({
            object : CoroutineVerticle() {
                private lateinit var client: RedisHeimdallSubscription
                override suspend fun start() {
                    val redisHeimdallSubscriptionOptions =
                        getDefaultRedisHeimdallSubscriptionOptions().addChannelNames("test-channel")
                    client = RedisHeimdallSubscription.create(vertx, redisHeimdallSubscriptionOptions) {}.await()
                }
            }
        }, deploymentOptionsOf()).await()
    }
}
