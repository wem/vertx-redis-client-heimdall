package ch.sourcemotion.vertx.redis.client.heimdall.testing


import io.vertx.core.Context
import io.vertx.core.Vertx
import io.vertx.core.eventbus.EventBus
import io.vertx.junit5.Checkpoint
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith

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

    /**
     * Because @Before* and @After* can get started concurrently otherwise
     */
    protected fun asyncBeforeOrAfter(block: suspend CoroutineScope.() -> Unit) {
        runBlocking(context.dispatcher()) {
            block()
        }
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

    protected fun VertxTestContext.asyncDelayed(
        checkpoints: Int,
        delay: Long = 2000,
        block: suspend CoroutineScope.(Checkpoint) -> Unit
    ) = async(checkpoint(checkpoints + 1)) { checkpoint ->
        val controlCheckpoint = checkpoint()
        block(checkpoint)
        // We start an own coroutine for the control checkpoint, so the usual test block can end and just the control
        // checkpoint is pending.
        launch {
            delay(delay)
            controlCheckpoint.flag()
        }
    }
}
