package ch.sourcemotion.vertx.redis.client.heimdall.testing.container

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.ToxiproxyContainer
import org.testcontainers.utility.DockerImageName

internal class TestContainer(dockerImageName: DockerImageName) : GenericContainer<TestContainer>(dockerImageName) {
    companion object {
        private val redisImageName = DockerImageName.parse("redis:${System.getenv("REDIS_VERSION")}")
        private val toxiProxyImageName = DockerImageName.parse("shopify/toxiproxy:2.1.4")

        const val REDIS_PORT = 6379


        fun createRedisContainer(network: Network): TestContainer =
            TestContainer(redisImageName).withExposedPorts(REDIS_PORT).withNetwork(network)

        fun createToxiProxyContainer(network: Network): ToxiproxyContainer =
            ToxiproxyContainer(toxiProxyImageName).withNetwork(network)
    }
}
