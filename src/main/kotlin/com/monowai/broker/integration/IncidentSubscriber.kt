package com.monowai.broker.integration

import com.monowai.broker.model.IncidentPayload
import com.monowai.broker.service.IncidentService
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.amqp.dsl.Amqp.inboundAdapter
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.Transformers
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.support.json.JacksonJsonObjectMapper

/**
 * Consumes incidents. Knows nothing about why the work failed, only that it did.
 */
@Configuration
class IncidentSubscriber(
    private val connectionFactory: ConnectionFactory,
    private val incidentService: IncidentService,
) {
    @Bean
    fun incidentToHandle(incidentQueue: Queue): IntegrationFlow =
        integrationFlow(
            inboundAdapter(connectionFactory, incidentQueue)
                .configureContainer {
                    it.concurrentConsumers(1)
                    // Keeps the incident in the same trace as the work that failed - see WorkSubscriber.
                    it.`object`.setObservationEnabled(true)
                },
        ) {
            // Spring Integration only deserialises `__TypeId__` classes from packages it has been told to trust.
            transform(
                Transformers
                    .fromJson(IncidentPayload::class.java, JacksonJsonObjectMapper())
                    .apply { setTrustedPackages(IncidentPayload::class.java.packageName) },
            )
            handle<IncidentPayload> { payload, _ -> incidentService.raiseIncident(payload) }
        }
}
