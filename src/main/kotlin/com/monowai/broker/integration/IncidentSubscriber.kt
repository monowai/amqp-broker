package com.monowai.broker.integration

import com.monowai.broker.model.IncidentPayload
import com.monowai.broker.model.Payload
import com.monowai.broker.service.IncidentService
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.integration.amqp.dsl.Amqp
import org.springframework.integration.amqp.dsl.SimpleMessageListenerContainerSpec
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.StandardIntegrationFlow
import org.springframework.integration.dsl.Transformers
import org.springframework.integration.support.json.JacksonJsonObjectMapper
import org.springframework.messaging.Message
import org.springframework.messaging.MessageHandler
import org.springframework.stereotype.Service

@Service
class IncidentSubscriber(
    private val connectionFactory: ConnectionFactory,
    private val incidentService: IncidentService,
) {
    @Bean
    fun incidentToHandle(incidentQueue: Queue): StandardIntegrationFlow =
        IntegrationFlow
            .from(
                Amqp
                    .inboundAdapter(connectionFactory, incidentQueue)
                    .configureContainer { c: SimpleMessageListenerContainerSpec ->
                        c.concurrentConsumers(1)
                    },
            ).transform(
                // Spring Integration only deserialises `__TypeId__` classes from packages it has been told to trust.
                Transformers
                    .fromJson(IncidentPayload::class.java, JacksonJsonObjectMapper())
                    .apply { setTrustedPackages(IncidentPayload::class.java.packageName) },
            ).handle(
                incidentHandler(),
            ).get()

    /**
     * Invoke the business service with the payload from the Message.
     */
    private fun incidentHandler(): MessageHandler =
        MessageHandler { message: Message<*> ->
            // The payload was already transformed by the integration flow.
            val payload = message.payload as Payload<*>
            incidentService.raiseIncident(payload)
        }
}
