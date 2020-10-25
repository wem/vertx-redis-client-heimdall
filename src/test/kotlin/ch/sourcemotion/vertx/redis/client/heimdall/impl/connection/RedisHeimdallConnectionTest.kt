package ch.sourcemotion.vertx.redis.client.heimdall.impl.connection

import ch.sourcemotion.vertx.redis.client.heimdall.testing.AbstractVertxTest
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.redis.client.Command
import io.vertx.redis.client.Request
import io.vertx.redis.client.Response
import io.vertx.redis.client.impl.RedisConnectionImpl
import io.vertx.redis.client.impl.types.ErrorType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.net.ConnectException

internal class RedisHeimdallConnectionTest : AbstractVertxTest() {

    /**
     * Tests more the mock as production code. But the basic concept is covered.
     */
    @Test
    internal fun connection_issue_handler_called_on_end() {
        // given
        val delegate = mockk<RedisConnectionImpl> {
            var mockEndHandler: Handler<Void?>? = null
            every { endHandler(any()) } answers {
                mockEndHandler = arg(0)
                this@mockk
            }

            every { end(any()) } answers {
                mockEndHandler?.handle(arg(0))
            }
        }

        // then
        RedisHeimdallConnection(delegate) {
            it.shouldBeInstanceOf<RedisHeimdallException>().reason.shouldBe(Reason.CONNECTION_ISSUE)
        }.initConnection()

        // when
        delegate.end(null)
    }

    @Test
    internal fun connection_issue_delegated_on_send() {
        // given
        val rootCause = Exception("send-root-cause")

        val delegate = mockk<RedisConnectionImpl> {
            var mockExceptionHandler: Handler<Throwable>? = null
            every { exceptionHandler(any()) } answers {
                mockExceptionHandler = arg(0)
                this@mockk
            }
            every { send(any(), any()) } answers {
                mockExceptionHandler?.handle(rootCause)
                this@mockk
            }
            every { endHandler(any()) } answers { this@mockk }
        }

        // then
        val connectionIssueHandler = Handler<Throwable> {
            val heimdallException = it.shouldBeInstanceOf<RedisHeimdallException>()
            verifyHeimdallException(heimdallException, Reason.CONNECTION_ISSUE, rootCause)
        }

        // then
        val sendResultHandler = Handler<AsyncResult<Response>> {
            it.succeeded().shouldBeFalse()
            val heimdallException = it.cause().shouldBeInstanceOf<RedisHeimdallException>()
            verifyHeimdallException(heimdallException, Reason.CONNECTION_ISSUE, rootCause)
        }

        // when
        val sut = RedisHeimdallConnection(delegate, connectionIssueHandler).initConnection()
        sut.send(Request.cmd(Command.COMMAND), sendResultHandler)
    }

    @Test
    internal fun connection_issue_delegated_on_batch() {
        // given
        val rootCause = Exception("send-root-cause")

        val delegate = mockk<RedisConnectionImpl> {
            var mockExceptionHandler: Handler<Throwable>? = null
            every { exceptionHandler(any()) } answers {
                mockExceptionHandler = arg(0)
                this@mockk
            }
            every { batch(any(), any()) } answers {
                mockExceptionHandler?.handle(rootCause)
                this@mockk
            }
            every { endHandler(any()) } answers { this@mockk }
        }

        // then
        val connectionIssueHandler = Handler<Throwable> {
            val heimdallException = it.shouldBeInstanceOf<RedisHeimdallException>()
            verifyHeimdallException(heimdallException, Reason.CONNECTION_ISSUE, rootCause)
        }

        // then
        val sendResultHandler = Handler<AsyncResult<List<Response>>> {
            it.succeeded().shouldBeFalse()
            val heimdallException = it.cause().shouldBeInstanceOf<RedisHeimdallException>()
            verifyHeimdallException(heimdallException, Reason.CONNECTION_ISSUE, rootCause)
        }

        // when
        val sut = RedisHeimdallConnection(delegate, connectionIssueHandler).initConnection()
        sut.batch(listOf(Request.cmd(Command.COMMAND)), sendResultHandler)
    }


    @Test
    internal fun connection_issue_delegated_connection_closed_on_send() {
        // given
        val rootCause = ErrorType.create("CONNECTION_CLOSED")

        val delegate = mockk<RedisConnectionImpl> {
            every { exceptionHandler(any()) } answers { this@mockk }
            every { send(any(), any()) } answers {
                val resultHandler: Handler<AsyncResult<Response>> = arg(1)
                resultHandler.handle(Future.failedFuture(rootCause))
                this@mockk
            }
            every { endHandler(any()) } answers { this@mockk }
        }

        // then
        val connectionIssueHandler = Handler<Throwable> {
            val heimdallException = it.shouldBeInstanceOf<RedisHeimdallException>()
            verifyHeimdallException(heimdallException, Reason.CONNECTION_ISSUE, rootCause)
        }

        // then
        val sendResultHandler = Handler<AsyncResult<Response>> {
            it.succeeded().shouldBeFalse()
            val heimdallException = it.cause().shouldBeInstanceOf<RedisHeimdallException>()
            verifyHeimdallException(heimdallException, Reason.CONNECTION_ISSUE, rootCause)
        }

        // when
        val sut = RedisHeimdallConnection(delegate, connectionIssueHandler).initConnection()
        sut.send(Request.cmd(Command.COMMAND), sendResultHandler)
    }

    @Test
    internal fun connection_issue_delegated_connection_closed_on_batch() {
        // given
        val rootCause = ErrorType.create("CONNECTION_CLOSED")

        val delegate = mockk<RedisConnectionImpl> {
            every { exceptionHandler(any()) } answers { this@mockk }
            every { batch(any(), any()) } answers {
                val resultHandler: Handler<AsyncResult<List<Response>>> = arg(1)
                resultHandler.handle(Future.failedFuture(rootCause))
                this@mockk
            }
            every { endHandler(any()) } answers { this@mockk }
        }

        // then
        val connectionIssueHandler = Handler<Throwable> {
            val heimdallException = it.shouldBeInstanceOf<RedisHeimdallException>()
            verifyHeimdallException(heimdallException, Reason.CONNECTION_ISSUE, rootCause)
        }

        // then
        val sendResultHandler = Handler<AsyncResult<List<Response>>> {
            it.succeeded().shouldBeFalse()
            val heimdallException = it.cause().shouldBeInstanceOf<RedisHeimdallException>()
            verifyHeimdallException(heimdallException, Reason.CONNECTION_ISSUE, rootCause)
        }

        // when
        val sut = RedisHeimdallConnection(delegate, connectionIssueHandler).initConnection()
        sut.batch(listOf(Request.cmd(Command.COMMAND)), sendResultHandler)
    }

    @Test
    internal fun protocol_failure_not_handled_as_connection_issue_on_send() {
        // given
        val rootCause = ErrorType.create("protocol-failure")

        val delegate = mockk<RedisConnectionImpl> {
            every { exceptionHandler(any()) } answers { this@mockk }
            every { send(any(), any()) } answers {
                val resultHandler: Handler<AsyncResult<Response>> = arg(1)
                resultHandler.handle(Future.failedFuture(rootCause))
                this@mockk
            }
            every { endHandler(any()) } answers { this@mockk }
        }

        // then NOT
        val connectionIssueHandler = Handler<Throwable> {
            fail("should not get called")
        }

        // then
        val sendResultHandler = Handler<AsyncResult<Response>> {
            it.succeeded().shouldBeFalse()
            it.cause().shouldBeInstanceOf<ErrorType>().shouldBe(rootCause)
        }

        // when
        val sut = RedisHeimdallConnection(delegate, connectionIssueHandler).initConnection()
        sut.send(Request.cmd(Command.COMMAND), sendResultHandler)
    }

    @Test
    internal fun protocol_failure_not_handled_as_connection_issue_on_batch() {
        // given
        val rootCause = ErrorType.create("protocol-failure")

        val delegate = mockk<RedisConnectionImpl> {
            every { exceptionHandler(any()) } answers { this@mockk }
            every { batch(any(), any()) } answers {
                val resultHandler: Handler<AsyncResult<List<Response>>> = arg(1)
                resultHandler.handle(Future.failedFuture(rootCause))
                this@mockk
            }
            every { endHandler(any()) } answers { this@mockk }
        }

        // then NOT
        val connectionIssueHandler = Handler<Throwable> {
            fail("should not get called")
        }

        // then
        val sendResultHandler = Handler<AsyncResult<List<Response>>> {
            it.succeeded().shouldBeFalse()
            it.cause().shouldBeInstanceOf<ErrorType>().shouldBe(rootCause)
        }

        // when
        val sut = RedisHeimdallConnection(delegate, connectionIssueHandler).initConnection()
        sut.batch(listOf(Request.cmd(Command.COMMAND)), sendResultHandler)
    }

    @Test
    internal fun proper_handling_underlying_failure_on_send() {
        // given
        val rootCause = ConnectException("native-exception")

        val delegate = mockk<RedisConnectionImpl> {
            every { exceptionHandler(any()) } answers { this@mockk }
            every { send(any(), any()) } answers {
                val resultHandler: Handler<AsyncResult<Response>> = arg(1)
                resultHandler.handle(Future.failedFuture(rootCause))
                this@mockk
            }
            every { endHandler(any()) } answers { this@mockk }
        }

        // then NOT
        val connectionIssueHandler = Handler<Throwable> {
            fail("should not get called")
        }

        // then
        val sendResultHandler = Handler<AsyncResult<Response>> {
            it.succeeded().shouldBeFalse()
            it.cause().shouldBeInstanceOf<RedisHeimdallException>().cause.shouldBe(rootCause)
        }

        // when
        val sut = RedisHeimdallConnection(delegate, connectionIssueHandler).initConnection()
        sut.send(Request.cmd(Command.COMMAND), sendResultHandler)
    }

    @Test
    internal fun proper_handling_underlying_failure_on_batch() {
        // given
        val rootCause = ConnectException("native-exception")

        val delegate = mockk<RedisConnectionImpl> {
            every { exceptionHandler(any()) } answers { this@mockk }
            every { batch(any(), any()) } answers {
                val resultHandler: Handler<AsyncResult<List<Response>>> = arg(1)
                resultHandler.handle(Future.failedFuture(rootCause))
                this@mockk
            }
            every { endHandler(any()) } answers { this@mockk }
        }

        // then NOT
        val connectionIssueHandler = Handler<Throwable> {
            fail("should not get called")
        }

        // then
        val sendResultHandler = Handler<AsyncResult<List<Response>>> {
            it.succeeded().shouldBeFalse()
            it.cause().shouldBeInstanceOf<RedisHeimdallException>().cause.shouldBe(rootCause)
        }

        // when
        val sut = RedisHeimdallConnection(delegate, connectionIssueHandler).initConnection()
        sut.batch(listOf(Request.cmd(Command.COMMAND)), sendResultHandler)
    }

    @Test
    internal fun successful_send() {
        // givem
        val response = mockk<Response> {
            every { this@mockk.toString() } returns "well-done"
        }

        val delegate = mockk<RedisConnectionImpl> {
            every { exceptionHandler(any()) } answers { this@mockk }
            every { send(any(), any()) } answers {
                val resultHandler: Handler<AsyncResult<Response>> = arg(1)
                resultHandler.handle(Future.succeededFuture(response))
                this@mockk
            }
            every { endHandler(any()) } answers { this@mockk }
        }

        // then NOT
        val connectionIssueHandler = Handler<Throwable> {
            fail("should not get called")
        }

        // then
        val sendResultHandler = Handler<AsyncResult<Response>> {
            it.succeeded().shouldBeTrue()
            "${it.result()}".shouldBe("$response")
        }

        // when
        val sut = RedisHeimdallConnection(delegate, connectionIssueHandler).initConnection()
        sut.send(Request.cmd(Command.COMMAND), sendResultHandler)
    }

    @Test
    internal fun successful_batch() {
        // given
        val response = mockk<Response> {
            every { this@mockk.toString() } returns "well-done"
        }

        val delegate = mockk<RedisConnectionImpl> {
            every { exceptionHandler(any()) } answers { this@mockk }
            every { batch(any(), any()) } answers {
                val resultHandler: Handler<AsyncResult<List<Response>>> = arg(1)
                resultHandler.handle(Future.succeededFuture(listOf(response)))
                this@mockk
            }
            every { endHandler(any()) } answers { this@mockk }
        }

        // then NOT
        val connectionIssueHandler = Handler<Throwable> {
            fail("should not get called")
        }

        // then
        val sendResultHandler = Handler<AsyncResult<List<Response>>> { responses ->
            responses.succeeded().shouldBeTrue()
            responses.result().forEach { "$it".shouldBe("$response") }
        }

        // when
        val sut = RedisHeimdallConnection(delegate, connectionIssueHandler).initConnection()
        sut.batch(listOf(Request.cmd(Command.COMMAND)), sendResultHandler)
    }


    private fun verifyHeimdallException(
        heimdallException: RedisHeimdallException,
        expectedReason: Reason,
        rootCause: Throwable
    ) {
        heimdallException.reason.shouldBe(expectedReason)
        val heimdallExceptionCause = heimdallException.cause
        heimdallExceptionCause.shouldNotBeNull()
        heimdallExceptionCause.message.shouldBe(rootCause.message)
    }
}
