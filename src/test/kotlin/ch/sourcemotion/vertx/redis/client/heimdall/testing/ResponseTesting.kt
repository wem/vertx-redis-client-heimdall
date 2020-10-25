package ch.sourcemotion.vertx.redis.client.heimdall.testing

import io.kotest.matchers.shouldBe
import io.vertx.redis.client.Response

fun Response.shouldBePongResponse() = toString().shouldBe("PONG")
