package ch.sourcemotion.vertx.redis.client.heimdall

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason.ACCESS_DURING_RECONNECT
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason.CONNECTION_ISSUE
import ch.sourcemotion.vertx.redis.client.heimdall.testing.AbstractRedisTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeBetween
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.coroutines.await
import io.vertx.redis.client.Command
import io.vertx.redis.client.Request
import io.vertx.redis.client.Response
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.LazyThreadSafetyMode.NONE

internal class RedisHeimdallLightTest : AbstractRedisTest() {

    private val defaultOptions by lazy(NONE) { getDefaultRedisHeimdallOptions().setReconnectInterval(100) }

    @AfterEach
    internal fun tearDown() = asyncBeforeOrAfter {
        removeConnectionIssues()
        val client = RedisHeimdallLight(vertx, defaultOptions)
        client.send(Request.cmd(Command.FLUSHALL)).await()
        client.close()
    }

    @Test
    internal fun connect(testContext: VertxTestContext) = testContext.async {
        val sut = RedisHeimdallLight(vertx, defaultOptions).markAsTestClient()
        val expectedConnection = sut.connect().await()
        sut.connect().await().shouldBe(expectedConnection)
        sut.connect().await().shouldBe(expectedConnection)
    }

    @Test
    internal fun send_burst_on_startup(testContext: VertxTestContext) = testContext.async(10000) { checkpoint ->
        val sut = RedisHeimdallLight(vertx, defaultOptions.apply { redisOptions.maxWaitingHandlers = 10000 })
            .markAsTestClient()

        repeat(10000) { idx ->
            launch {
                sut.sendCmd().await().also {
                    testContext.verifySendResponse(it, idx + 1)
                }
                checkpoint.flag()
            }
        }
    }

    @Test
    internal fun batch_burst_on_startup(testContext: VertxTestContext) = testContext.async(10000) { checkpoint ->
        val sut = RedisHeimdallLight(vertx, defaultOptions.apply { redisOptions.maxWaitingHandlers = 10000 })
            .markAsTestClient()

        repeat(10000) { idx ->
            launch {
                sut.sendBatch().await().also {
                    testContext.verifyBatchResponse(it, idx + 1)
                }
                checkpoint.flag()
            }
        }
    }

    @Test
    internal fun send_reconnected_after_event(testContext: VertxTestContext) =
        testContext.async(1) { removeConnectionIssuesCheckpoint ->
            val commandAfterReconnectCheckpoint = testContext.checkpoint()
            val sut = RedisHeimdallLight(vertx, defaultOptions).markAsTestClient()

            vertx.eventBus().consumer<Unit>(defaultOptions.reconnectingStartNotificationAddress) {
                vertx.setTimer(defaultOptions.reconnectInterval) { removeConnectionIssues() }
                removeConnectionIssuesCheckpoint.flag()
            }

            vertx.eventBus().consumer<Unit>(defaultOptions.reconnectingSucceededNotificationAddress) {
                testScope.launch {
                    runCatching {
                        sut.sendCmd().await().also {
                            testContext.verifySendResponse(it, 2)
                            commandAfterReconnectCheckpoint.flag()
                        }
                    }
                }
            }

            sut.sendCmd().await().also { testContext.verifySendResponse(it, 1) }
            closeConnection()
            shouldThrow<RedisHeimdallException> { sut.sendCmd().await() }
        }

    @Test
    internal fun batch_reconnected_after_event(testContext: VertxTestContext) =
        testContext.async(1) { removeConnectionIssuesCheckpoint ->
            val commandAfterReconnectCheckpoint = testContext.checkpoint()
            val sut = RedisHeimdallLight(vertx, defaultOptions).markAsTestClient()

            vertx.eventBus().consumer<Unit>(defaultOptions.reconnectingStartNotificationAddress) {
                vertx.setTimer(defaultOptions.reconnectInterval) { removeConnectionIssues() }
                removeConnectionIssuesCheckpoint.flag()
            }

            vertx.eventBus().consumer<Unit>(defaultOptions.reconnectingSucceededNotificationAddress) {
                testScope.launch {
                    runCatching {
                        sut.sendBatch().await().also {
                            testContext.verifyBatchResponse(it, 2)
                            commandAfterReconnectCheckpoint.flag()
                        }
                    }
                }
            }

            sut.sendBatch().await().also { testContext.verifyBatchResponse(it, 1) }
            closeConnection()
            shouldThrow<RedisHeimdallException> { sut.sendBatch().await() }
        }

    @Test
    internal fun send_connection_issue_before_create(testContext: VertxTestContext) =
        testContext.async(1000) { checkpoint ->
            closeConnection()

            val sut = RedisHeimdallLight(vertx, defaultOptions).markAsTestClient()

            repeat(1000) {
                launch {
                    sut.sendCmd().onFailure {
                        testContext.verify {
                            val cause = it.shouldBeInstanceOf<RedisHeimdallException>()
                            cause.reason.shouldBe(CONNECTION_ISSUE)
                        }
                        checkpoint.flag()
                    }.onSuccess { testContext.failNow("Commands on initial connection issue should fail") }
                }
            }
        }

    @Test
    internal fun batch_connection_issue_before_create(testContext: VertxTestContext) =
        testContext.async(1000) { checkpoint ->
            closeConnection()

            val sut = RedisHeimdallLight(vertx, defaultOptions).markAsTestClient()

            repeat(1000) {
                launch {
                    sut.sendBatch().onFailure {
                        testContext.verify {
                            val cause = it.shouldBeInstanceOf<RedisHeimdallException>()
                            cause.reason.shouldBe(CONNECTION_ISSUE)
                        }
                        checkpoint.flag()
                    }.onSuccess { testContext.failNow("Commands on initial connection issue should fail") }
                }
            }
        }

    @Test
    internal fun send_connection_issue_after_some_commands(testContext: VertxTestContext) =
        testContext.async(1000) { checkpoint ->
            val sut = RedisHeimdallLight(vertx, defaultOptions).markAsTestClient()
            coroutineScope {
                repeat(1000) { idx ->
                    if (idx == 500) {
                        closeConnection()
                    }
                    launch {
                        // Some commands will fail, but all must get executed and if success the result must be
                        // in the expected range
                        runCatching { sut.sendCmd().await() }
                            .onSuccess {
                                testContext.verify { it.shouldNotBeNull().toInteger().shouldBeBetween(1, 1000) }
                            }
                            .onFailure {
                                testContext.verify {
                                    val cause = it.shouldBeInstanceOf<RedisHeimdallException>()
                                    cause.reason.shouldBeIn(ACCESS_DURING_RECONNECT, CONNECTION_ISSUE)
                                }
                            }
                        checkpoint.flag()
                    }
                }
                removeConnectionIssues()
            }
        }

    @Test
    internal fun batch_connection_issue_after_some_commands(testContext: VertxTestContext) =
        testContext.async(1000) { checkpoint ->
            val sut = RedisHeimdallLight(vertx, defaultOptions).markAsTestClient()
            coroutineScope {
                repeat(1000) { idx ->
                    if (idx == 500) {
                        closeConnection()
                    }
                    launch {
                        // Some commands will fail, but all must get executed and if success the result must be
                        // in the expected range
                        runCatching { sut.sendBatch().await() }
                            .onSuccess {
                                testContext.verify {
                                    it.shouldHaveSize(1).first().shouldNotBeNull().toInteger().shouldBeBetween(1, 1000)
                                }
                            }
                            .onFailure {
                                testContext.verify {
                                    val cause = it.shouldBeInstanceOf<RedisHeimdallException>()
                                    cause.reason.shouldBeIn(ACCESS_DURING_RECONNECT, CONNECTION_ISSUE)
                                }
                            }
                        checkpoint.flag()
                    }
                }
                removeConnectionIssues()
            }
        }

    private fun VertxTestContext.verifyBatchResponse(responseList: List<Response?>, expectedValue: Int) {
        verify { responseList.shouldHaveSize(1).first().shouldNotBeNull().toInteger().shouldBe(expectedValue) }
    }

    private fun VertxTestContext.verifySendResponse(response: Response?, expectedValue: Int) {
        verify { response.shouldNotBeNull().toInteger().shouldBe(expectedValue) }
    }

    private fun RedisHeimdall.sendCmd() =
        send(Request.cmd(Command.INCR).arg("key"))

    private fun RedisHeimdall.sendBatch() =
        batch(listOf(Request.cmd(Command.INCR).arg("key")))
}