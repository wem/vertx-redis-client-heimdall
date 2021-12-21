package ch.sourcemotion.vertx.redis.client.heimdall

import ch.sourcemotion.vertx.redis.client.heimdall.impl.PostReconnectJob
import ch.sourcemotion.vertx.redis.client.heimdall.impl.RedisHeimdallImpl
import io.vertx.core.*
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisConnection
import io.vertx.redis.client.Request
import io.vertx.redis.client.Response
import java.util.*

/**
 * Lightweight but more performant variant of this client. This client will use only a single connection to Redis.
 * Supports reconnect capabilities like [RedisHeimdall] as it is used under hood.
 */
class RedisHeimdallLight(
    vertx: Vertx,
    options: RedisHeimdallOptions
) : RedisHeimdall, PostReconnectJob {

    private var redisHeimdall = RedisHeimdallImpl(vertx, options, listOf(this))
    private var started = false
    private var connection: RedisConnection? = null
    private val pendingStartCommands = ArrayDeque<PendingCommand>()

    init {
        mayStart()
    }

    override fun send(command: Request?): Future<Response> {
        val p = Promise.promise<Response>()
        val conn = connection
        when {
            conn != null -> conn.send(command)
                .onFailure { cause ->
                    handleIfConnectionIssue(cause)
                    p.fail(cause)
                }.onSuccess { response ->
                    p.complete(response)
                }
            started.not() -> pendingStartCommands.add {
                it.send(command)
                    .onFailure { cause ->
                        handleIfConnectionIssue(cause)
                        p.fail(cause)
                    }.onSuccess { response ->
                        p.complete(response)
                    }
            }
            redisHeimdall.reconnectingInProgress ->
                p.fail(RedisHeimdallException(RedisHeimdallException.Reason.ACCESS_DURING_RECONNECT))
            else -> {
                p.fail(RedisHeimdallException(RedisHeimdallException.Reason.CONNECTION_ISSUE))
            }
        }

        return p.future()
    }

    override fun batch(commands: List<Request>?): Future<List<Response>> {
        val p = Promise.promise<List<Response>>()
        val conn = connection
        when {
            conn != null -> conn.batch(commands)
                .onFailure { cause ->
                    handleIfConnectionIssue(cause)
                    p.fail(cause)
                }.onSuccess { responses ->
                    p.complete(responses)
                }
            started.not() -> pendingStartCommands.add {
                it.batch(commands)
                    .onFailure { cause ->
                        handleIfConnectionIssue(cause)
                        p.fail(cause)
                    }.onSuccess { responses ->
                        p.complete(responses)
                    }
            }
            redisHeimdall.reconnectingInProgress ->
                p.fail(RedisHeimdallException(RedisHeimdallException.Reason.ACCESS_DURING_RECONNECT))
            else -> {
                p.fail(RedisHeimdallException(RedisHeimdallException.Reason.CONNECTION_ISSUE))
            }
        }
        return p.future()
    }

    override fun connect(): Future<RedisConnection> {
        val p = Promise.promise<RedisConnection>()
        val conn = connection
        when {
            conn != null -> p.complete(conn)
            started.not() -> pendingStartCommands.add {
                p.complete(it)
            }
            redisHeimdall.reconnectingInProgress ->
                p.fail(RedisHeimdallException(RedisHeimdallException.Reason.ACCESS_DURING_RECONNECT))
            else -> {
                p.fail(RedisHeimdallException(RedisHeimdallException.Reason.CONNECTION_ISSUE))
            }
        }
        return p.future()
    }

    private fun handleIfConnectionIssue(cause: Throwable) {
        if (cause is RedisHeimdallException && cause.reason == RedisHeimdallException.Reason.CONNECTION_ISSUE) {
            connection?.close() // Return the connection to pool
            connection = null
        }
    }

    /**
     * Called after successful reconnect by [redisHeimdall]. This will ensure a valid connection got obtained
     * before any information about successful reconnection get published elsewhere.
     */
    override fun execute(redis: Redis): Future<Unit> {
        val p = Promise.promise<Unit>()
        getConnection().onSuccess {
            connection = it
            mayStart(it).onComplete(p)
        }.onFailure {
            p.fail(it)
        }
        return p.future()
    }

    private fun getConnection(): Future<RedisConnection> {
        val p = Promise.promise<RedisConnection>()
        if (connection != null) {
            p.complete(connection)
        } else {
            redisHeimdall.connect { result ->
                if (result.succeeded()) {
                    p.complete(result.result())
                } else {
                    p.fail(result.cause())
                }
            }
        }
        return p.future()
    }

    /**
     * Tries to start the client.
     * If there is a connection issue during start, it will get called later again by [execute] when the client got reconnected.
     */
    private fun mayStart(connection: RedisConnection? = null): Future<Unit> {
        val p = Promise.promise<Unit>()
        when {
            started -> p.complete()
            connection != null -> runCatching { executePendingStartCommands(connection) }
                .onSuccess { p.complete() }
                .onFailure { p.fail(it) }
            else -> getConnection().onSuccess { conn ->
                executePendingStartCommands(conn)
                this.connection = conn
                started = true
                p.complete()
            }
        }
        return p.future()
    }

    private fun executePendingStartCommands(connection: RedisConnection) {
        if (started.not()) {
            var pendingCommand: PendingCommand? = pendingStartCommands.poll()
            while (pendingCommand != null) {
                pendingCommand.execute(connection)
                pendingCommand = pendingStartCommands.poll()
            }
            started = true
        }
    }

    override fun close() {
        connection?.close()
        redisHeimdall.close()
    }
}

private fun interface PendingCommand {
    fun execute(connection: RedisConnection)
}