package ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription

import ch.sourcemotion.vertx.redis.client.heimdall.testing.AbstractVertxTest
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.connection.RedisSubscriptionHeimdallConnection
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.junit5.VertxTestContext
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisConnection
import org.junit.jupiter.api.Test

internal class SubscriptionPostReconnectJobTest : AbstractVertxTest() {

    private val sut = SubscriptionPostReconnectJob

    @Test
    internal fun subscription_after_reconnect_successful(testContext: VertxTestContext) =
        testContext.async(1) { checkpoint ->
            // given
            val heimdallConnection = createHeimdallConnection()
            val redis = createSuccessfulConnectRedis(heimdallConnection)

            // when & then
            sut.execute(redis) {
                testContext.verify { it.succeeded().shouldBeTrue() }
                checkpoint.flag()
            }
        }

    @Test
    internal fun subscription_after_reconnect_fail(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        // given
        val subscriptionFailureCase = Exception("subscription-failure")
        val heimdallConnection = createHeimdallConnection(subscriptionFailureCase)
        val redis = createSuccessfulConnectRedis(heimdallConnection)

        // when & then
        sut.execute(redis) {
            testContext.verify {
                it.succeeded().shouldBeFalse()
                val subscriptionFailCause = it.cause().shouldBeInstanceOf<java.lang.Exception>()
                subscriptionFailCause.message.shouldBe(subscriptionFailureCase.message)
            }
            checkpoint.flag()
        }
    }

    @Test
    internal fun redis_connect_failed(testContext: VertxTestContext) = testContext.async(1) { checkpoint ->
        // given
        val rootCause = Exception("Test-connect-failure")
        val redis = createFailingConnectRedis(rootCause)

        // when & then
        sut.execute(redis) {
            testContext.verify {
                it.succeeded().shouldBeFalse()
                val heimdallException = it.cause().shouldBeInstanceOf<java.lang.Exception>()
                heimdallException.message.shouldBe(rootCause.message)
            }
            checkpoint.flag()
        }
    }

    @Test
    internal fun subscription_after_reconnect_successful_even_wrong_connection_type(testContext: VertxTestContext) =
        testContext.async(1) { checkpoint ->
            // given
            val redis = createSuccessfulConnectRedis(createNonHeimdallConnection())

            // when & then
            sut.execute(redis) {
                testContext.verify { it.succeeded().shouldBeTrue() }
                checkpoint.flag()
            }
        }

    private fun createNonHeimdallConnection() = mockk<RedisConnection>()

    private fun createHeimdallConnection(reconnectFailureCase: Throwable? = null) =
        mockk<RedisSubscriptionHeimdallConnection> {
            every { subscribeAfterReconnect(any()) } answers {
                val handler = arg<Handler<AsyncResult<Unit>>>(0)
                if (reconnectFailureCase != null) {
                    handler.handle(Future.failedFuture(reconnectFailureCase))
                } else {
                    handler.handle(Future.succeededFuture(Unit))
                }
            }
        }

    private fun createSuccessfulConnectRedis(connection: RedisConnection) = mockk<Redis> {
        every { connect(any()) } answers {
            val handler = arg<Handler<AsyncResult<RedisConnection>>>(0)
            this@mockk.also { handler.handle(Future.succeededFuture(connection)) }
        }
    }

    private fun createFailingConnectRedis(cause: Throwable) = mockk<Redis> {
        every { connect(any()) } answers {
            val handler = arg<Handler<AsyncResult<RedisConnection>>>(0)
            this@mockk.also { handler.handle(Future.failedFuture(cause)) }
        }
    }
}
