package com.monowai.broker

import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.integration.amqp.dsl.Amqp.inboundAdapter
import org.springframework.integration.amqp.dsl.SimpleMessageListenerContainerSpec
import org.springframework.integration.dsl.IntegrationFlows
import org.springframework.integration.dsl.StandardIntegrationFlow
import org.springframework.integration.dsl.Transformers
import org.springframework.integration.support.json.Jackson2JsonObjectMapper
import org.springframework.messaging.Message
import org.springframework.messaging.MessageHandler
import org.springframework.stereotype.Service

@Service
class WorkSubscriber(
    private val connectionFactory: ConnectionFactory,
    private val workService: WorkService
) {
    @Bean
    fun workToDo(workQueue: Queue): StandardIntegrationFlow? {
        return IntegrationFlows.from(
            inboundAdapter(connectionFactory, workQueue)
                .configureContainer { c: SimpleMessageListenerContainerSpec ->
                    c.concurrentConsumers(1)
                }
        ).transform(
            Transformers.fromJson(WorkPayload::class.java, Jackson2JsonObjectMapper())
        ).handle(
            workHandler()
        ).get()
    }

    /**
     * Invoke the business service with the payload from the Message.
     */
    private fun workHandler(): MessageHandler {
        return MessageHandler { message: Message<*> ->
            // The payload was already transformed by the integration flow.
            val workPayload = message.payload as WorkPayload
            workService.doSomeWork(workPayload)
        }
    }
}
