package ch.sourcemotion.vertx.redis.client.heimdall.testing

import ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions
import ch.sourcemotion.vertx.redis.client.heimdall.testing.container.TestContainer
import eu.rekawek.toxiproxy.model.ToxicDirection
import io.vertx.core.logging.LoggerFactory
import io.vertx.junit5.VertxExtension
import io.vertx.kotlin.redis.client.redisOptionsOf
import kotlinx.coroutines.delay
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
internal abstract class AbstractRedisTest : AbstractVertxTest() {

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractRedisTest::class.java)

        private const val UPSTREAM_LIMIT_TOXIC_NAME = "upstream-limit"
        private const val DOWNSTREAM_LIMIT_TOXIC_NAME = "downstream-limit"
        private const val DOWNSTREAM_TIMEOUT_TOXIC_NAME = "downstream-timeout"
        private const val UPSTREAM_TIMEOUT_TOXIC_NAME = "upstream-timeout"
        private val network = Network.newNetwork()

        @JvmStatic
        @Container
        val redisContainer = TestContainer.createRedisContainer(network)
    }

    @Container
    val toxiProxyContainer = TestContainer.createToxiProxyContainer(network)

    private val redisProxy by lazy { toxiProxyContainer.getProxy(redisContainer, TestContainer.REDIS_PORT) }

    fun getDefaultRedisHeimdallOptions() =
        RedisHeimdallOptions(redisOptionsOf(connectionString = "redis://${redisProxy.containerIpAddress}:${redisProxy.proxyPort}"))

    fun removeConnectionIssues() {
        redisProxy.toxics().all.forEach { it.remove() }
    }

    suspend fun downStreamTimeout() {
        timeout(DOWNSTREAM_TIMEOUT_TOXIC_NAME, ToxicDirection.DOWNSTREAM)
        // Ensure toxiproxy git enough time to close
        delay(2)
    }

    suspend fun upStreamTimeout() {
        timeout(UPSTREAM_TIMEOUT_TOXIC_NAME, ToxicDirection.UPSTREAM)
        // Ensure toxiproxy git enough time to close
        delay(2)
    }

    private fun timeout(name: String, direction: ToxicDirection) {
        redisProxy.toxics().timeout(name, direction, 1)
    }

    suspend fun closeAndResumeConnection(options: RedisHeimdallOptions) {
        val connectionIssueDurationSeconds = options.reconnectInterval / 1000
        logger.info("Simulate connection issue. Close the connection for $connectionIssueDurationSeconds seconds. " +
                "Resume afterwards the connectivity and return after further $connectionIssueDurationSeconds seconds")
        downStreamTimeout()
        // Ensure reconnect process started
        delay(options.reconnectInterval * 2)
        removeConnectionIssues()
        // Give enough time to reconnect
        delay(options.reconnectInterval * 2)
    }
}
