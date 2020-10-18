package ch.sourcemotion.vertx.redis.client.resilient

import ch.sourcemotion.vertx.redis.client.resilient.container.TestContainer
import eu.rekawek.toxiproxy.model.ToxicDirection
import io.vertx.kotlin.redis.client.redisOptionsOf
import kotlinx.coroutines.delay
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
internal abstract class AbstractRedisTest : AbstractVertxTest() {

    companion object {
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

    fun getDefaultRedisOptions() =
        RedisResilientOptions(redisOptionsOf(connectionString = "redis://${redisProxy.containerIpAddress}:${redisProxy.proxyPort}"))

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
}
