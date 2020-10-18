package ch.sourcemotion.vertx.redis.client.resilient

import io.vertx.core.Context
import io.vertx.core.Vertx
import io.vertx.core.eventbus.EventBus
import io.vertx.junit5.Checkpoint
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(VertxExtension::class)
internal abstract class AbstractVertxTest {

    @Volatile
    protected lateinit var vertx: Vertx

    @Volatile
    protected lateinit var context: Context

    @Volatile
    protected lateinit var testScope: CoroutineScope

    @Volatile
    protected lateinit var eventBus: EventBus

    @BeforeEach
    internal fun setUpVertx(vertx: Vertx) {
        this.vertx = vertx
        eventBus = vertx.eventBus()
        context = vertx.orCreateContext
        testScope = CoroutineScope(context.dispatcher())
    }


    protected fun VertxTestContext.async(block: suspend CoroutineScope.() -> Unit) {
        testScope.launch {
            runCatching { block() }
                .onSuccess { completeNow() }
                .onFailure { failNow(it) }
        }
    }

    protected fun VertxTestContext.async(
        checkpoint: Checkpoint,
        block: suspend CoroutineScope.(Checkpoint) -> Unit
    ) {
        testScope.launch {
            runCatching { block(checkpoint) }
                .onSuccess { checkpoint.flag() }
                .onFailure { failNow(it) }
        }
    }

    protected fun VertxTestContext.async(
        checkpoints: Int,
        block: suspend CoroutineScope.(Checkpoint) -> Unit
    ) = async(checkpoint(checkpoints + 1), block)

    /**
     * Async test delegate function with a delayed, so the test will not end with the last call of [Checkpoint.flag]
     * on the test checkpoint, but on call [Checkpoint.flag] on control checkpoint.
     * This way too many calls on the test checkpoint will result in failing test.
     */
    protected fun VertxTestContext.asyncTestDelayedEnd(
        checkpoints: Int,
        delayMillis: Long = 2000,
        block: suspend (Checkpoint) -> Unit
    ) {
        val doubleCheckpoint = DoubleCheckpoint.create(vertx, this, checkpoints, delayMillis)
        testScope.launch {
            runCatching { block(doubleCheckpoint) }
                .onFailure { failNow(it) }
        }
    }
}

private class DoubleCheckpoint private constructor(
    requiredNumberOfPasses: Int,
    private val vertx: Vertx,
    private val testCheckpoint: Checkpoint,
    private val controlCheckpoint: Checkpoint,
    private val delayMillis: Long
) : Checkpoint {

    companion object {
        fun create(
            vertx: Vertx,
            testContext: VertxTestContext,
            requiredNumberOfPasses: Int,
            delayMillis: Long
        ): Checkpoint {
            return DoubleCheckpoint(
                requiredNumberOfPasses,
                vertx,
                testContext.checkpoint(requiredNumberOfPasses),
                testContext.checkpoint(),
                delayMillis
            )
        }
    }

    private val missingPasses = AtomicInteger(requiredNumberOfPasses)

    override fun flag() {
        testCheckpoint.flag()
        if (missingPasses.decrementAndGet() == 0) {
            vertx.setTimer(delayMillis) {
                controlCheckpoint.flag()
            }
        }
    }
}
