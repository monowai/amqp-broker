package com.monowai.broker

import com.monowai.broker.AmqpPlumbing.Companion.workRoutingKey
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.core.Exchange
import org.springframework.context.annotation.Bean
import org.springframework.integration.amqp.dsl.Amqp.outboundAdapter
import org.springframework.integration.annotation.MessagingGateway
import org.springframework.integration.dsl.IntegrationFlows
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.dsl.StandardIntegrationFlow
import org.springframework.integration.json.ObjectToJsonTransformer
import org.springframework.messaging.MessageChannel
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

/**
 * Wires up the integration flow to publish a payload to a Queue
 */
@Service
class WorkPublisher {

    /**
     * This is an arbitrary name used to start the flow.
     */
    @Bean
    fun sendWork(): MessageChannel {
        return MessageChannels.direct().get()
    }

    @Bean
    fun workPublisherFlow(
        sendWork: MessageChannel,
        amqpTemplate: AmqpTemplate,
        exchange: Exchange,
    ): StandardIntegrationFlow {
        return IntegrationFlows.from(sendWork)
            .transform(ObjectToJsonTransformer()) // sent as Json
            .handle(
                outboundAdapter(amqpTemplate)
                    .exchangeName(exchange.name) // To this exchange
                    .routingKey(workRoutingKey) // via this route
            )
            .get()
    }

    @MessagingGateway(defaultRequestChannel = "sendWork")
    @Component
    interface WorkGateway {
        fun publish(workPayload: WorkPayload)
    }
}
