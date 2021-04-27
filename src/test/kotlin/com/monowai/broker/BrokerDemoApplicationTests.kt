package com.monowai.broker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.monowai.broker.integration.WorkPublisher
import com.monowai.broker.model.WorkPayload
import com.monowai.broker.service.WorkService
import com.rabbitmq.client.impl.LongStringHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.verify
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest
class BrokerDemoApplicationTests {

    @Autowired
    lateinit var qpidMemoryBroker: QpidMemoryBroker

    @Autowired
    lateinit var publisher: WorkPublisher.WorkGateway

    @Autowired
    lateinit var rabbitTemplate: RabbitTemplate

    @MockBean
    lateinit var workService: WorkService

    private val objectMapper = ObjectMapper().registerModule(KotlinModule())

    @Test
    fun sendAndReceive() {
        val first = WorkPayload("1", "Test Payload")
        val second = WorkPayload("2", "Test Payload")
        publisher.publish(first)
        publisher.publish(second)
        Thread.sleep(1000)
        verify(workService, atLeast(1)).doSomeWork(first)
        verify(workService, atLeast(1)).doSomeWork(second)
    }

    @Test
    fun sendFailureRoutesToDlq() {
        val workPayload = WorkPayload("error", "Error Payload")
        val exception = RuntimeException("Something Went Wrong")
        Mockito.`when`(workService.doSomeWork(workPayload)).thenThrow(exception)
        publisher.publish(workPayload)
        Thread.sleep(1000)
        verify(workService, atLeast(1)).doSomeWork(workPayload)

        val message = rabbitTemplate.receive("work-dlq")
        assertThat(message).isNotNull.hasFieldOrProperty("body")

        val fromDlq = objectMapper.readValue(message?.body, WorkPayload::class.java)

        assertThat(fromDlq).usingRecursiveComparison().isEqualTo(workPayload)
        val exceptionMessage = (
            LongStringHelper
                .asLongString(message?.messageProperties!!.headers["x-exception-stacktrace"].toString()) as Any
            ).toString()
        assertThat(exceptionMessage).isNotNull.contains(exception.message)
    }
}
