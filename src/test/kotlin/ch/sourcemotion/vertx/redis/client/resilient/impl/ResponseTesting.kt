package ch.sourcemotion.vertx.redis.client.resilient.impl

import io.kotest.matchers.shouldBe
import io.vertx.redis.client.Response

fun Response.shouldBePongResponse() = toString().shouldBe("PONG")
