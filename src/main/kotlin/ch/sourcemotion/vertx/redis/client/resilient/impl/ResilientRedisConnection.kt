package ch.sourcemotion.vertx.redis.client.resilient.impl

import ch.sourcemotion.vertx.redis.client.resilient.RedisResilientException
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.redis.client.RedisConnection
import io.vertx.redis.client.Request
import io.vertx.redis.client.Response
import io.vertx.redis.client.impl.types.ErrorType

internal class ResilientRedisConnection(
    private val delegate: RedisConnection,
    private val connectionIssueHandler: Handler<Throwable>
) : RedisConnection by delegate {
    override fun send(command: Request, onSend: Handler<AsyncResult<Response>>): RedisConnection {
        val handlerResumeCaller = HandlerResumeCaller(onSend)
        delegate.exceptionHandler {
            val exception = RedisResilientException(cause = it)
            connectionIssueHandler.handle(exception)
            handlerResumeCaller.callOnce(Future.failedFuture(RedisResilientException(cause = exception)))
        }
        delegate.send(command) {
            interceptConnectionIssues(it, handlerResumeCaller)
        }
        return this
    }

    override fun batch(
        commands: MutableList<Request>,
        onSend: Handler<AsyncResult<MutableList<Response>>>
    ): RedisConnection {
        val handlerResumeCaller = HandlerResumeCaller(onSend)
        delegate.exceptionHandler {
            val exception = RedisResilientException(cause = it)
            connectionIssueHandler.handle(exception)
            handlerResumeCaller.callOnce(Future.failedFuture(RedisResilientException(cause = exception)))
        }
        delegate.batch(commands) {
            interceptConnectionIssues(it, handlerResumeCaller)
        }
        return this
    }

    /**
     * Interception of the Redis command result. We need to catch the situation where the connection was closed without
     * calling the connections exceptionhandler.
     */
    private fun <R> interceptConnectionIssues(operation: AsyncResult<R>, handlerResumeCaller: HandlerResumeCaller<R>) {
        if (operation.failed()) {
            val cause = operation.cause()
            val finalCause = if (cause is ErrorType) {
                if (cause.toString() == "CONNECTION_CLOSED") {
                    RedisResilientException(cause.toString(), cause).also { connectionIssueHandler.handle(it) }
                } else cause
                // Normalize the exception. Only ErrorType or RedisResilientException should be returned
            } else RedisResilientException(cause = cause)
            handlerResumeCaller.callOnce(Future.failedFuture(finalCause))
        } else {
            handlerResumeCaller.callOnce(operation)
        }
    }
}

private class HandlerResumeCaller<R>(val handler: Handler<AsyncResult<R>>, private var called: Boolean = false) {
    fun callOnce(result: AsyncResult<R>) {
        if (called.not()) {
            called = true
            handler.handle(result)
        }
    }
}
