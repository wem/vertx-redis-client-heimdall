package ch.sourcemotion.vertx.redis.client.heimdall.impl.connection

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException
import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException.Reason
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Promise
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

    /**
     * Open function where specific tasks can be done before the connection will be used.
     */
    open fun initConnection(): RedisHeimdallConnection {
        endHandler(::onConnectionEnd)
        return this
    }

    override fun send(command: Request): Future<Response> {
        val promise = Promise.promise<Response>()
        delegate.exceptionHandler {
            val exception = RedisHeimdallException(
                Reason.CONNECTION_ISSUE,
                "Connection issue catched by exception handler",
                cause = it
            )
            connectionIssueHandler.handle(exception)
            promise.tryFail(exception)
        }
        delegate.send(command)
            .onSuccess { promise.tryComplete(it) }
            .onFailure { mapConnectionIssueException(promise, it) }
        return promise.future()
    }

    override fun batch(commands: List<Request>): Future<List<Response>> {
        val promise = Promise.promise<List<Response>>()
        delegate.exceptionHandler {
            val exception = RedisHeimdallException(Reason.CONNECTION_ISSUE, cause = it)
            connectionIssueHandler.handle(exception)
            promise.tryFail(exception)
        }
        delegate.batch(commands)
            .onSuccess { promise.tryComplete(it) }
            .onFailure { mapConnectionIssueException(promise, it) }
        return promise.future()
    }

    /**
     * Maps the underlying Vert.x Redis exception or failure case to the corresponding Redis Heimdall.
     * We need to catch the situation where the connection was closed without calling [RedisConnection.exceptionHandler].
     */
    private fun mapConnectionIssueException(promise: Promise<*>, cause: Throwable) {
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
        promise.tryFail(finalCause)
    }

    private fun onConnectionEnd(void: Void?) {
        connectionIssueHandler.handle(RedisHeimdallException(Reason.CONNECTION_ISSUE, "Connection did end"))
    }
}