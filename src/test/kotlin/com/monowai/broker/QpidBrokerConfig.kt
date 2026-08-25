package com.monowai.broker

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar

/**
 * Starts an in-memory broker for the test context and tears it down with it.
 *
 * Import this into a test rather than relying on component scanning, so it is obvious where the
 * broker comes from.
 */
@TestConfiguration
class QpidBrokerConfig {
    /**
     * destroyMethod is inferred from AutoCloseable, but say it out loud - a leaked broker holds a
     * port and a thread pool for the life of the JVM.
     */
    @Bean(destroyMethod = "close")
    fun qpidMemoryBroker(
        @Value($$"${spring.rabbitmq.username:guest}") username: String,
        @Value($$"${spring.rabbitmq.password:guest}") password: String,
    ): QpidMemoryBroker = QpidMemoryBroker(QpidMemoryBroker.EPHEMERAL_PORT, username, password)

    /**
     * Chicken and egg: Spring needs spring.rabbitmq.port to build a ConnectionFactory, but the port
     * is not known until the broker has bound. A DynamicPropertyRegistrar is initialised ahead of
     * ordinary singletons, so asking for the broker here forces it up first, and the bound port is
     * published into the Environment before anything tries to connect.
     */
    @Bean
    fun qpidPortRegistrar(qpidMemoryBroker: QpidMemoryBroker): DynamicPropertyRegistrar =
        DynamicPropertyRegistrar { registry ->
            registry.add("spring.rabbitmq.port") { qpidMemoryBroker.port }
        }
}
