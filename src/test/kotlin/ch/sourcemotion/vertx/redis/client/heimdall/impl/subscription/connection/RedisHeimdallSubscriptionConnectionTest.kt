package ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.connection

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.ClientInstanceId
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.SubscriptionStore
import ch.sourcemotion.vertx.redis.client.heimdall.impl.subscription.SubscriptionStore.Companion.createSubscriptionStore
import ch.sourcemotion.vertx.redis.client.heimdall.testing.AbstractVertxTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.junit5.VertxTestContext
import io.vertx.redis.client.Command
import io.vertx.redis.client.RedisConnection
import io.vertx.redis.client.Request
import io.vertx.redis.client.Response
import io.vertx.redis.client.impl.types.ErrorType
import org.junit.jupiter.api.Test
import java.nio.channels.ClosedChannelException

internal class RedisHeimdallSubscriptionConnectionTest : AbstractVertxTest() {

    @Test
    internal fun register_message_handler_not_permitted() {
        shouldThrow<RedisHeimdallException> { createSubscriptionConnection().handler { } }
    }

    @Test
    internal fun send_non_subscription_cmd_not_permitted() {
        createSubscriptionConnection().send(Request.cmd(Command.COMMAND)) {
            it.failed().shouldBeTrue()
            it.cause().shouldBeInstanceOf<RedisHeimdallException>()
        }
    }

    @Test
    internal fun batch_non_subscription_cmd_not_permitted() {
        createSubscriptionConnection().batch(listOf(Request.cmd(Command.COMMAND), Request.cmd(Command.PING))) {
            it.failed().shouldBeTrue()
            it.cause().shouldBeInstanceOf<RedisHeimdallException>()
        }
    }

    @Test
    internal fun send_subscription_cmd_permitted() {
        createSubscriptionConnection().send(Request.cmd(Command.SUBSCRIBE)) {
            it.succeeded().shouldBeTrue()
        }
        createSubscriptionConnection().send(Request.cmd(Command.PSUBSCRIBE)) {
            it.succeeded().shouldBeTrue()
        }
        createSubscriptionConnection().send(Request.cmd(Command.UNSUBSCRIBE)) {
            it.succeeded().shouldBeTrue()
        }
        createSubscriptionConnection().send(Request.cmd(Command.PUNSUBSCRIBE)) {
            it.succeeded().shouldBeTrue()
        }
    }

    @Test
    internal fun batch_subscription_cmd_permitted() {
        val delegate = createRedisConnectionMock()
        createSubscriptionConnection(delegate = delegate).batch(
            listOf(
                Request.cmd(Command.SUBSCRIBE), Request.cmd(Command.PSUBSCRIBE),
                Request.cmd(Command.UNSUBSCRIBE), Request.cmd(Command.PSUBSCRIBE)
            )
        ) {
            it.succeeded().shouldBeTrue()
        }
    }

    @Test
    internal fun subscribe_after_reconnect_successful_no_channels(testContext: VertxTestContext) =
        testContext.async {
            // given
            val clientInstanceId = ClientInstanceId("a571a8a0-1594-4635-8cc1-51646c5b334d")
            val subscriptionStore = vertx.createSubscriptionStore(clientInstanceId)

            val sut = createSubscriptionConnection(
                subscriptionStore = subscriptionStore,
            )

            // when & then
            sut.subscribeAfterReconnect {
                it.succeeded().shouldBeTrue()
            }
        }

    @Test
    internal fun subscribe_after_reconnect_successful_multiple_channels(testContext: VertxTestContext) =
        testContext.async {
            // given
            val channels = listOf("channel-one", "channel-two")
            val clientInstanceId = ClientInstanceId("a571a8a0-1594-4635-8cc1-51646c5b334d")
            val subscriptionStore = vertx.createSubscriptionStore(clientInstanceId)
            channels.forEach { subscriptionStore.addSubscription(it) }

            val sut = createSubscriptionConnection(
                subscriptionStore = subscriptionStore,
            )

            // when & then
            sut.subscribeAfterReconnect {
                it.succeeded().shouldBeTrue()
            }
        }

    @Test
    internal fun correct_reason_on_general_exception_when_subscribe_after_reconnect(testContext: VertxTestContext) =
        testContext.async {
            // given
            val rootCause = Exception("subscribe_after_reconnect_failed-root-cause")

            val delegate = createRedisConnectionMock(rootCause)

            val channels = listOf("channel-one", "channel-two")
            val clientInstanceId = ClientInstanceId("a571a8a0-1594-4635-8cc1-51646c5b334d")
            val subscriptionStore = vertx.createSubscriptionStore(clientInstanceId)
            channels.forEach { subscriptionStore.addSubscription(it) }

            val sut = createSubscriptionConnection(
                subscriptionStore = subscriptionStore,
                delegate = delegate
            )

            // when & then
            sut.subscribeAfterReconnect {
                verifyFailedAndExpectedReason(it, Reason.UNSPECIFIED)
            }
        }

    @Test
    internal fun correct_reason_on_closed_connection_exception_when_subscribe_after_reconnect(testContext: VertxTestContext) =
        testContext.async {
            // given
            val rootCause = ClosedChannelException()

            val delegate = createRedisConnectionMock(rootCause)

            val channels = listOf("channel-one", "channel-two")
            val clientInstanceId = ClientInstanceId("a571a8a0-1594-4635-8cc1-51646c5b334d")
            val subscriptionStore = vertx.createSubscriptionStore(clientInstanceId)
            channels.forEach { subscriptionStore.addSubscription(it) }

            val sut = createSubscriptionConnection(
                subscriptionStore = subscriptionStore,
                delegate = delegate
            )

            // when & then
            sut.subscribeAfterReconnect {
                verifyFailedAndExpectedReason(it, Reason.CONNECTION_ISSUE)
            }
        }

    @Test
    internal fun correct_reason_on_error_type_exception_when_subscribe_after_reconnect(testContext: VertxTestContext) =
        testContext.async {
            // given
            val rootCause = ErrorType.create("protocol error")

            val delegate = createRedisConnectionMock(rootCause)

            val channels = listOf("channel-one", "channel-two")
            val clientInstanceId = ClientInstanceId("a571a8a0-1594-4635-8cc1-51646c5b334d")
            val subscriptionStore = vertx.createSubscriptionStore(clientInstanceId)
            channels.forEach { subscriptionStore.addSubscription(it) }

            val sut = createSubscriptionConnection(
                subscriptionStore = subscriptionStore,
                delegate = delegate
            )

            // when & then
            sut.subscribeAfterReconnect {
                it.failed().shouldBeTrue()
                val cause = it.cause()
                cause.shouldBeInstanceOf<ErrorType>()
                cause.message.shouldBe(rootCause.message)
            }
        }

    @Test
    internal fun correct_reason_on_CONNECTION_CLOSED_exception_when_subscribe_after_reconnect(testContext: VertxTestContext) =
        testContext.async {
            // given
            val rootCause = ErrorType.create("CONNECTION_CLOSED")

            val delegate = createRedisConnectionMock(rootCause)

            val channels = listOf("channel-one", "channel-two")
            val clientInstanceId = ClientInstanceId("a571a8a0-1594-4635-8cc1-51646c5b334d")
            val subscriptionStore = vertx.createSubscriptionStore(clientInstanceId)
            channels.forEach { subscriptionStore.addSubscription(it) }

            val sut = createSubscriptionConnection(
                subscriptionStore = subscriptionStore,
                delegate = delegate
            )

            // when & then
            sut.subscribeAfterReconnect {
                verifyFailedAndExpectedReason(it, Reason.CONNECTION_ISSUE)
            }
        }

    @Test
    internal fun correct_reason_on_internal_exception_when_subscribe_after_reconnect(testContext: VertxTestContext) =
        testContext.async {
            // given
            val rootCause = RedisHeimdallException(Reason.INTERNAL)

            val delegate = createRedisConnectionMock(rootCause)

            val channels = listOf("channel-one", "channel-two")
            val clientInstanceId = ClientInstanceId("a571a8a0-1594-4635-8cc1-51646c5b334d")
            val subscriptionStore = vertx.createSubscriptionStore(clientInstanceId)
            channels.forEach { subscriptionStore.addSubscription(it) }

            val sut = createSubscriptionConnection(
                subscriptionStore = subscriptionStore,
                delegate = delegate
            )

            // when & then
            sut.subscribeAfterReconnect {
                verifyFailedAndExpectedReason(it, Reason.INTERNAL)
            }
        }

    private fun verifyFailedAndExpectedReason(it: AsyncResult<Unit>, reason: Reason) {
        it.failed().shouldBeTrue()
        val cause = it.cause()
        cause.shouldBeInstanceOf<RedisHeimdallException>()
        cause.reason.shouldBe(reason)
    }

    private fun createSubscriptionConnection(
        delegate: RedisConnection = createRedisConnectionMock(),
        connectionIssueHandler: Handler<Throwable> = mockk {
            every { handle(any()) } returns Unit
        },
        subscriptionStore: SubscriptionStore = mockk(),
        messageHandler: Handler<Response> = mockk()
    ): RedisHeimdallSubscriptionConnection {
        return RedisHeimdallSubscriptionConnection(
            delegate,
            connectionIssueHandler,
            subscriptionStore,
            messageHandler
        ).initConnection() as RedisHeimdallSubscriptionConnection
    }

    private fun createRedisConnectionMock(sendBatchCause: Throwable? = null, block: (RedisConnection.() -> Unit)? = null) =
        mockk<RedisConnection> {
            every { endHandler(any()) } returns this@mockk
            every { handler(any()) } returns this@mockk
            every { exceptionHandler(any()) } returns this@mockk
            if (sendBatchCause != null) {
                every { send(any(), any()) } answers {
                    val handler: Handler<AsyncResult<Any>> = arg(1)
                    handler.handle(Future.failedFuture(sendBatchCause))
                    this@mockk
                }
                every { batch(any(), any()) } answers {
                    val handler: Handler<AsyncResult<Any>> = arg(1)
                    handler.handle(Future.failedFuture(sendBatchCause))
                    this@mockk
                }
            } else {
                every { send(any(), any()) } answers {
                    val handler: Handler<AsyncResult<Any>> = arg(1)
                    handler.handle(Future.succeededFuture())
                    this@mockk
                }
                every { batch(any(), any()) } answers {
                    val handler: Handler<AsyncResult<Any>> = arg(1)
                    handler.handle(Future.succeededFuture())
                    this@mockk
                }
            }
            block?.invoke(this)
        }
}
