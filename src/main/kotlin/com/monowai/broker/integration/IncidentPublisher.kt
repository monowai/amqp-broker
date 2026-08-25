package com.monowai.broker.integration

import com.monowai.broker.integration.AmqpPlumbing.Companion.INCIDENT_ROUTE
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.core.Exchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.amqp.dsl.Amqp.outboundAdapter
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.json.ObjectToJsonTransformer

/**
 * Announces a processing failure.
 *
 * Deliberately a separate route to the DLQ. The DLQ owns the failed message so it can be replayed;
 * the incident is a lightweight notification that something needs a human. Neither knows about the
 * other, which is the point.
 */
@Configuration
class IncidentPublisher {
    @Bean
    fun incidentPublisherFlow(
        amqpTemplate: AmqpTemplate,
        primaryExchange: Exchange,
    ): IntegrationFlow =
        integrationFlow<IncidentGateway> {
            transform(ObjectToJsonTransformer()) // sent as Json
            handle(
                outboundAdapter(amqpTemplate)
                    .exchangeName(primaryExchange.name)
                    .routingKey(INCIDENT_ROUTE),
            )
        }
}
