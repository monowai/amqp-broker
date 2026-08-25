package com.monowai.broker

import com.monowai.broker.integration.WorkGateway
import com.monowai.broker.model.IncidentPayload
import com.monowai.broker.model.WorkPayload
import com.monowai.broker.service.IncidentService
import com.monowai.broker.service.WorkService
import com.rabbitmq.client.impl.LongStringHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

@SpringBootTest
@Import(QpidBrokerConfig::class)
class BrokerDemoApplicationTests {
    @Autowired
    lateinit var qpidMemoryBroker: QpidMemoryBroker

    @Autowired
    lateinit var publisher: WorkGateway

    @Autowired
    lateinit var rabbitTemplate: RabbitTemplate

    @MockitoBean
    lateinit var workService: WorkService

    @MockitoBean
    lateinit var incidentService: IncidentService

    private val objectMapper = JsonMapper.builder().addModule(kotlinModule()).build()

    @Test
    fun sendAndReceive() {
        val first = WorkPayload("1", "Test Payload")
        val second = WorkPayload("2", "Test Payload")
        publisher.publish(first)
        publisher.publish(second)
        awaitAsserted {
            verify(workService, atLeast(1)).doSomeWork(first)
            verify(workService, atLeast(1)).doSomeWork(second)
        }
    }

    @Test
    fun sendFailureRoutesToDlq() {
        val workPayload = WorkPayload("error", "Error Payload")
        val exception = RuntimeException("Something Went Wrong")
        Mockito.`when`(workService.doSomeWork(workPayload)).thenThrow(exception)
        publisher.publish(workPayload)
        awaitAsserted { verify(workService, atLeast(1)).doSomeWork(workPayload) }

        // Blocking receive. The DLQ hop is asynchronous, so ask the broker to wait rather than
        // guessing how long it takes.
        val message = rabbitTemplate.receive(WORK_DLQ, AWAIT_TIMEOUT_MS)
        assertThat(message).isNotNull.hasFieldOrProperty("body")

        val fromDlq = objectMapper.readValue(message?.body, WorkPayload::class.java)

        assertThat(fromDlq).usingRecursiveComparison().isEqualTo(workPayload)
        val exceptionMessage =
            (
                LongStringHelper
                    .asLongString(message?.messageProperties!!.headers["x-exception-stacktrace"].toString()) as Any
            ).toString()
        assertThat(exceptionMessage).isNotNull.contains(exception.message)

        // The failure is also announced on its own route. The Incident service knows nothing about AMQP,
        // and the incident id is the original WorkPayload.id carried across as the AMQP correlationId.
        awaitAsserted {
            verify(incidentService, atLeast(1))
                .raiseIncident(IncidentPayload(workPayload.id, exception.message!!))
        }
    }

    @Test
    fun successRaisesNoIncident() {
        val payload = WorkPayload("no-incident", "Test Payload")
        publisher.publish(payload)

        // Once the service has seen it and not thrown, the recoverer never ran, so nothing is still
        // in flight that could raise an incident. That makes the negative assertion below a fact
        // rather than a guess about how long to wait.
        awaitAsserted { verify(workService, atLeast(1)).doSomeWork(payload) }
        verifyNoInteractions(incidentService)
    }

    /**
     * Retry the assertion until it holds or we run out of patience.
     *
     * Everything here crosses a broker, so the alternative is a fixed sleep - which is either slower
     * than it needs to be or too short on a loaded CI box, and usually both.
     */
    private fun awaitAsserted(assertion: () -> Unit) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (true) {
            try {
                assertion()
                return
            } catch (error: AssertionError) {
                if (System.currentTimeMillis() >= deadline) {
                    throw error
                }
                Thread.sleep(POLL_MS)
            }
        }
    }

    companion object {
        private const val WORK_DLQ = "work-dlq"
        private const val AWAIT_TIMEOUT_MS = 5000L
        private const val POLL_MS = 25L
    }
}
