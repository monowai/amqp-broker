package com.monowai.broker.integration

import com.monowai.broker.model.WorkPayload
import com.monowai.broker.service.WorkService
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.amqp.dsl.Amqp.inboundAdapter
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.Transformers
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.support.json.JacksonJsonObjectMapper

/**
 * Consumes work and hands it to the business service.
 */
@Configuration
class WorkSubscriber(
    private val connectionFactory: ConnectionFactory,
    private val workService: WorkService,
    private val workInterceptor: StatelessRetryOperationsInterceptor,
) {
    @Bean
    fun workToHandle(workQueue: Queue): IntegrationFlow =
        integrationFlow(
            inboundAdapter(connectionFactory, workQueue)
                .configureContainer {
                    it.concurrentConsumers(1)
                    it.adviceChain(workInterceptor)
                    // The DSL builds its own container, so spring.rabbitmq.listener.* never reaches it.
                    // Without this the consumer starts a fresh trace and the publisher's is orphaned.
                    it.`object`.setObservationEnabled(true)
                },
        ) {
            // Spring Integration only deserialises `__TypeId__` classes from packages it has been told to trust.
            transform(
                Transformers
                    .fromJson(WorkPayload::class.java, JacksonJsonObjectMapper())
                    .apply { setTrustedPackages(WorkPayload::class.java.packageName) },
            )
            // Typed. No `message.payload as WorkPayload`, so a change to the transform above is a
            // compile error rather than a ClassCastException on the first bad message.
            handle<WorkPayload> { payload, _ ->
                try {
                    workService.doSomeWork(payload)
                } catch (e: RuntimeException) {
                    // Tell the container not to requeue. workInterceptor's recoverer takes it from here.
                    throw AmqpRejectAndDontRequeueException(e)
                }
            }
        }
}
