package com.monowai.broker

import com.monowai.broker.integration.WorkPublisher
import com.monowai.broker.model.WorkPayload
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * Tracing you do not assert on is tracing that quietly stops working.
 *
 * These tests use the real WorkService, which throws on id "2", so the whole failure path runs and
 * the spans it produces are the ones production would produce.
 */
@SpringBootTest
@Import(QpidBrokerConfig::class, TracingTestConfig::class)
class TracingTest {
    @Autowired
    lateinit var publisher: WorkPublisher.WorkGateway

    @Autowired
    lateinit var spans: InMemorySpanExporter

    @Autowired
    lateinit var rabbitTemplate: RabbitTemplate

    @BeforeEach
    fun clearSpans() {
        while (rabbitTemplate.receive(WORK_DLQ) != null) {
            // Drain anything a previous test parked, so the DLQ read below is unambiguous.
        }
        spans.reset()
    }

    @Test
    fun traceContextCrossesTheBroker() {
        publisher.publish(WorkPayload("traced", "Test Payload"))

        val captured = awaitSpans(2)
        val send = captured.named("demoExchange/work send")
        val receive = captured.named("work receive")

        // The publisher and the consumer are different threads, and in production different
        // processes. Same trace means the W3C traceparent header survived the round trip.
        assertThat(receive.traceId).isEqualTo(send.traceId)
        assertThat(receive.parentSpanId).isEqualTo(send.spanId)
        assertThat(captured.map { it.traceId }.distinct()).hasSize(1)
    }

    @Test
    fun failurePathStaysInOneTrace() {
        // WorkService throws on id "2" - no mocking, this is the real failure.
        publisher.publish(WorkPayload("2", "Error Payload"))

        val captured = awaitSpans(5)

        // The whole story - published, consumed, failed, parked on the DLQ, incident raised and
        // handled - is a single trace. That is the entire argument for doing this.
        assertThat(captured.map { it.name })
            .containsExactlyInAnyOrder(
                "demoExchange/work send",
                "work receive",
                "demoExchange/work-dlq send",
                "demoExchange/incident send",
                "incident receive",
            )
        assertThat(captured.map { it.traceId }.distinct()).hasSize(1)

        val receive = captured.named("work receive")
        // Both failure routes hang off the consume that failed, not off each other.
        assertThat(captured.named("demoExchange/work-dlq send").parentSpanId).isEqualTo(receive.spanId)
        assertThat(captured.named("demoExchange/incident send").parentSpanId).isEqualTo(receive.spanId)
        assertThat(captured.named("incident receive").parentSpanId)
            .isEqualTo(captured.named("demoExchange/incident send").spanId)
    }

    @Test
    fun theFailedConsumeIsMarkedInError() {
        publisher.publish(WorkPayload("2", "Error Payload"))

        val receive = awaitSpans(5).named("work receive")

        // Recovering a message counts as handling it, so the span is only red because
        // DemoRepublishMessageRecoverer says so.
        assertThat(receive.status.statusCode).isEqualTo(StatusCode.ERROR)
        assertThat(receive.events.map { it.name }).contains("exception")
    }

    @Test
    fun theDeadLetteredMessageCarriesTheTrace() {
        publisher.publish(WorkPayload("2", "Error Payload"))
        val captured = awaitSpans(5)

        val dead = rabbitTemplate.receive(WORK_DLQ)
        assertThat(dead).isNotNull

        // Whatever replays this message can join the trace that produced it rather than starting
        // a new one that nobody can tie back to the original failure.
        val traceParent =
            dead
                ?.messageProperties
                ?.headers
                ?.get("traceparent")
                .toString()
        assertThat(traceParent).contains(captured.first().traceId)
    }

    private fun awaitSpans(count: Int): List<SpanData> {
        val deadline = System.currentTimeMillis() + SPAN_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline && spans.finishedSpanItems.size < count) {
            Thread.sleep(POLL_MS)
        }
        // Return whatever we have. A short list makes the assertion report what was missing.
        return spans.finishedSpanItems
    }

    private fun List<SpanData>.named(name: String): SpanData =
        singleOrNull { it.name == name }
            ?: throw AssertionError("Expected exactly one '$name' span, got ${map { it.name }}")

    companion object {
        private const val WORK_DLQ = "work-dlq"
        private const val SPAN_TIMEOUT_MS = 5000L
        private const val POLL_MS = 25L
    }
}
