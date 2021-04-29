package com.monowai.broker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Exchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.integration.config.EnableIntegration
import org.springframework.retry.interceptor.RetryOperationsInterceptor
import org.springframework.stereotype.Component

/**
 * These beans create resources on the MessageBroker.
 */
@EnableIntegration
@Component
class AmqpPlumbing {

    companion object {
        const val workRoute = "work"
        const val workRouteErr = "$workRoute-dlq"
        const val incidentRoute = "incident"
        const val durable = false
        const val autoDelete = false
    }

    @Bean
    fun primaryExchange(): Exchange {
        return DirectExchange("demoExchange")
    }

    @Bean
    fun workQueue(): Queue {
        // You should think about your queue characteristics, don't just copy and paste this
        return Queue(workRoute, durable, false, autoDelete)
    }

    @Bean
    fun workBinding(workQueue: Queue, primaryExchange: Exchange): Binding {
        return BindingBuilder
            .bind(workQueue)
            .to(primaryExchange)
            .with(workQueue.name)
            .noargs()
    }

    @Bean
    fun workDlQueue(): Queue {
        // You should think about your queue characteristics, don't just copy and paste this
        return Queue(workRouteErr, durable, false, autoDelete)
    }

    @Bean
    fun workDlqBinding(workDlQueue: Queue, primaryExchange: Exchange): Binding {
        return BindingBuilder
            .bind(workDlQueue)
            .to(primaryExchange)
            .with(workRouteErr)
            .noargs()
    }

    @Bean
    fun incidentQueue(): Queue {
        return Queue(incidentRoute, durable, false, autoDelete)
    }

    @Bean
    fun incidentBinding(incidentQueue: Queue, primaryExchange: Exchange): Binding {
        return BindingBuilder
            .bind(incidentQueue)
            .to(primaryExchange)
            .with(incidentRoute)
            .noargs()
    }

    @Bean
    fun workInterceptor(amqpTemplate: AmqpTemplate, primaryExchange: Exchange): RetryOperationsInterceptor {
        // Route work to the DLQ if an error occurs
        return RetryInterceptorBuilder.stateless()
            .maxAttempts(1)
            .recoverer(DemoRepublishMessageRecoverer(amqpTemplate, primaryExchange.name, workRouteErr))
            .build()
    }

    /**
     * Overriding the standard Spring ObjectMapper to provider richer support for Kotlin data.
     */
    @Bean
    fun getObjectMapper(): ObjectMapper {
        return ObjectMapper().registerModule(KotlinModule())
    }
}
