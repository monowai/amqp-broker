package com.monowai.broker.integration

import com.monowai.broker.integration.AmqpPlumbing.Companion.WORK_ROUTE
import com.monowai.broker.model.WorkPayload
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.core.Exchange
import org.springframework.amqp.support.AmqpHeaders
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.amqp.dsl.Amqp.outboundAdapter
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.json.ObjectToJsonTransformer

/**
 * Publishes a payload to the work queue.
 */
@Configuration
class WorkPublisher {
    /**
     * Starting a flow from an interface makes [WorkGateway] the flow's input. No channel bean, no
     * `defaultRequestChannel = "someString"`, nothing to keep in sync.
     */
    @Bean
    fun workPublisherFlow(
        amqpTemplate: AmqpTemplate,
        primaryExchange: Exchange,
    ): IntegrationFlow =
        integrationFlow<WorkGateway> {
            // Promote Payload.id to the AMQP correlationId while the payload is still typed. Anything
            // downstream - including error handling - can then correlate without parsing the body.
            enrichHeaders { headerFunction<WorkPayload>(AmqpHeaders.CORRELATION_ID) { it.payload.id } }
            transform(ObjectToJsonTransformer()) // sent as Json
            handle(
                outboundAdapter(amqpTemplate)
                    .exchangeName(primaryExchange.name) // To this exchange
                    .routingKey(WORK_ROUTE), // via this route
            )
        }
}
