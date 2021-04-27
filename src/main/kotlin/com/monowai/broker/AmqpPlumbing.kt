package com.monowai.broker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Exchange
import org.springframework.amqp.core.Queue
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
        const val workRoutingKey = "work"
    }

    @Bean
    fun exchange(): Exchange {
        return DirectExchange("demoExchange")
    }

    @Bean
    fun workQueue(): Queue {
        // You should think about your queue characteristics, don't just copy and paste this
        return Queue("workQueue", false, false, true)
    }

    @Bean
    fun workBinding(workQueue: Queue, exchange: Exchange): Binding {
        return BindingBuilder
            .bind(workQueue)
            .to(exchange)
            .with(workRoutingKey)
            .noargs()
    }

    /**
     * Overriding the standard Spring ObjectMapper to provider richer support for Kotlin data.
     */
    @Bean
    fun getObjectMapper(): ObjectMapper {
        return ObjectMapper().registerModule(KotlinModule())
    }
}
