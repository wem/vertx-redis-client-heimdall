package ch.sourcemotion.vertx.redis.client.heimdall.impl.connection

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.client.RedisConnection
import io.vertx.redis.client.Request
import io.vertx.redis.client.Response
import io.vertx.redis.client.impl.types.ErrorType
import java.io.IOException
import java.nio.channels.ClosedChannelException

internal open class RedisHeimdallConnection(
    private val delegate: RedisConnection,
    private val connectionIssueHandler: Handler<Throwable>
) : RedisConnection by delegate {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(RedisHeimdallConnection::class.java)
    }

    /**
     * Open function where specific tasks can be done before the connection will be used.
     */
    open fun initConnection(): RedisHeimdallConnection {
        endHandler(::onConnectionEnd)
        return this
    }

    override fun send(command: Request, onSend: Handler<AsyncResult<Response>>): RedisConnection {
        val handlerWrapper = ExecutionResultHandlerWrapper(onSend)
        delegate.exceptionHandler {
            logger.debug("Exception handler called on command $command")
            val exception = RedisHeimdallException(
                Reason.CONNECTION_ISSUE,
                "Connection issue catched by exception handler",
                cause = it
            )
            connectionIssueHandler.handle(exception)
            handlerWrapper.handle(Future.failedFuture(exception))
        }
        delegate.send(command) { response ->
            interceptConnectionIssues(response, handlerWrapper)
        }
        return this
    }

    override fun batch(
        commands: List<Request>,
        onSend: Handler<AsyncResult<List<Response>>>
    ): RedisConnection {
        val handlerWrapper = ExecutionResultHandlerWrapper(onSend)
        delegate.exceptionHandler {
            val exception = RedisHeimdallException(Reason.CONNECTION_ISSUE, cause = it)
            connectionIssueHandler.handle(exception)
            handlerWrapper.handle(Future.failedFuture(exception))
        }
        delegate.batch(commands) { response ->
            interceptConnectionIssues(response, handlerWrapper)
        }
        return this
    }

    /**
     * Interception of the Redis command result. We need to catch the situation where the connection was closed without
     * calling [RedisConnection.exceptionHandler].
     */
    private fun <R> interceptConnectionIssues(response: AsyncResult<R>, handler: Handler<AsyncResult<R>>) {
        if (response.failed()) {
            val cause = response.cause()

            // Normalize the exception. Only ErrorType or RedisResilientException should be returned
            val finalCause = if (cause is ErrorType) {
                // We start reconnect process on CONNECTION_CLOSED
                if (cause.toString() == "CONNECTION_CLOSED") {
                    RedisHeimdallException(Reason.CONNECTION_ISSUE, cause.toString(), cause)
                        .also { connectionIssueHandler.handle(it) }
                } else cause
            } else if (cause is RedisHeimdallException) {
                cause
            } else if (cause is ClosedChannelException) {
                RedisHeimdallException(Reason.CONNECTION_ISSUE, cause.message, cause)
                    .also { connectionIssueHandler.handle(it) }
            } else if (cause is IOException && cause.message == "Broken pipe") {
                RedisHeimdallException(Reason.CONNECTION_ISSUE, cause.message, cause)
                    .also { connectionIssueHandler.handle(it) }
            } else RedisHeimdallException(Reason.UNSPECIFIED, cause.message, cause)
            handler.handle(Future.failedFuture(finalCause))
        } else {
            handler.handle(response)
        }
    }

    private fun onConnectionEnd(void: Void?) {
        connectionIssueHandler.handle(RedisHeimdallException(Reason.CONNECTION_ISSUE, "Connection did end"))
    }
}

private class ExecutionResultHandlerWrapper<T>(private val delegate: Handler<AsyncResult<T>>) :
    Handler<AsyncResult<T>> by delegate {
    private var delegateCalled = false

    override fun handle(event: AsyncResult<T>?) {
        if (delegateCalled.not()) {
            delegateCalled = true
            delegate.handle(event)
        }
    }
}
