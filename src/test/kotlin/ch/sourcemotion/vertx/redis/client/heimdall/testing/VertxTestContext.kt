package ch.sourcemotion.vertx.redis.client.heimdall.testing

import io.vertx.core.Future
import io.vertx.junit5.VertxTestContext

fun VertxTestContext.assertSuccess(future: Future<*>): Future<*> = future.onSuccess { completeNow() }.onFailure { failNow(it) }