package com.monowai.broker.integration

import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Exchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.integration.config.EnableIntegration
import org.springframework.stereotype.Component

/**
 * These beans create resources on the MessageBroker.
 */
@EnableIntegration
@Component
class AmqpPlumbing {
    companion object {
        const val WORK_ROUTE = "work"
        const val WORK_ROUTE_ERR = "$WORK_ROUTE-dlq"
        const val INCIDENT_ROUTE = "incident"
        const val DURABLE = false
        const val AUTO_DELETE = false
    }

    @Bean
    fun primaryExchange(): Exchange = DirectExchange("demoExchange")

    @Bean
    fun workQueue(): Queue {
        // You should think about your queue characteristics, don't just copy and paste this
        return Queue(WORK_ROUTE, DURABLE, false, AUTO_DELETE)
    }

    @Bean
    fun workBinding(
        workQueue: Queue,
        primaryExchange: Exchange,
    ): Binding =
        BindingBuilder
            .bind(workQueue)
            .to(primaryExchange)
            .with(workQueue.name)
            .noargs()

    @Bean
    fun workDlQueue(): Queue {
        // You should think about your queue characteristics, don't just copy and paste this
        return Queue(WORK_ROUTE_ERR, DURABLE, false, AUTO_DELETE)
    }

    @Bean
    fun workDlqBinding(
        workDlQueue: Queue,
        primaryExchange: Exchange,
    ): Binding =
        BindingBuilder
            .bind(workDlQueue)
            .to(primaryExchange)
            .with(WORK_ROUTE_ERR)
            .noargs()

    @Bean
    fun incidentQueue(): Queue = Queue(INCIDENT_ROUTE, DURABLE, false, AUTO_DELETE)

    @Bean
    fun incidentBinding(
        incidentQueue: Queue,
        primaryExchange: Exchange,
    ): Binding =
        BindingBuilder
            .bind(incidentQueue)
            .to(primaryExchange)
            .with(INCIDENT_ROUTE)
            .noargs()

    @Bean
    fun workInterceptor(
        amqpTemplate: AmqpTemplate,
        primaryExchange: Exchange,
        incidentGateway: IncidentPublisher.IncidentGateway,
    ): StatelessRetryOperationsInterceptor {
        // Route work to the DLQ if an error occurs. A single delivery attempt, so no retries.
        // Spring AMQP 4 omits the x-exception-stacktrace header unless includeStackTrace is opted into.
        val recoverer =
            DemoRepublishMessageRecoverer(amqpTemplate, primaryExchange.name, WORK_ROUTE_ERR, incidentGateway)
                .includeStackTrace(true)
        return RetryInterceptorBuilder
            .stateless()
            .maxRetries(0)
            .recoverer(recoverer)
            .build()
    }
}
