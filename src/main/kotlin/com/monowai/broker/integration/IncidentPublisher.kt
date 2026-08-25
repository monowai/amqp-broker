package com.monowai.broker.integration

import com.monowai.broker.integration.AmqpPlumbing.Companion.INCIDENT_ROUTE
import com.monowai.broker.model.IncidentPayload
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.core.Exchange
import org.springframework.context.annotation.Bean
import org.springframework.integration.amqp.dsl.Amqp.outboundAdapter
import org.springframework.integration.annotation.MessagingGateway
import org.springframework.integration.dsl.DirectChannelSpec
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.json.ObjectToJsonTransformer
import org.springframework.messaging.MessageChannel
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

/**
 * Wires up the integration flow that announces a processing failure.
 *
 * This is deliberately a separate route to the DLQ. The DLQ owns the failed message so it can be
 * replayed; the incident is a lightweight notification that something needs a human. Neither knows
 * about the other, which is the point.
 */
@Service
class IncidentPublisher {
    /**
     * This is an arbitrary name used to start the flow.
     */
    @Bean
    fun sendIncident(): DirectChannelSpec = MessageChannels.direct()

    @Bean
    fun incidentPublisherFlow(
        sendIncident: MessageChannel,
        amqpTemplate: AmqpTemplate,
        primaryExchange: Exchange,
    ): IntegrationFlow =
        IntegrationFlow
            .from(sendIncident)
            .transform(ObjectToJsonTransformer()) // sent as Json
            .handle(
                outboundAdapter(amqpTemplate)
                    .exchangeName(primaryExchange.name) // To this exchange
                    .routingKey(INCIDENT_ROUTE), // via this route
            ).get()

    @MessagingGateway(defaultRequestChannel = "sendIncident")
    @Component
    interface IncidentGateway {
        fun publish(incidentPayload: IncidentPayload)
    }
}
