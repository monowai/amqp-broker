package com.monowai.broker.integration

import com.monowai.broker.model.WorkPayload
import com.monowai.broker.service.WorkService
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.integration.amqp.dsl.Amqp.inboundAdapter
import org.springframework.integration.amqp.dsl.SimpleMessageListenerContainerSpec
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.StandardIntegrationFlow
import org.springframework.integration.dsl.Transformers
import org.springframework.integration.support.json.JacksonJsonObjectMapper
import org.springframework.messaging.Message
import org.springframework.messaging.MessageHandler
import org.springframework.stereotype.Service

@Service
class WorkSubscriber(
    private val connectionFactory: ConnectionFactory,
    private val workService: WorkService,
    private val workInterceptor: StatelessRetryOperationsInterceptor,
) {
    @Bean
    fun workToHandle(workQueue: Queue): StandardIntegrationFlow =
        IntegrationFlow
            .from(
                inboundAdapter(connectionFactory, workQueue)
                    .configureContainer { c: SimpleMessageListenerContainerSpec ->
                        c.concurrentConsumers(1)
                        c.adviceChain(workInterceptor)
                    },
            ).transform(
                // Spring Integration only deserialises `__TypeId__` classes from packages it has been told to trust.
                Transformers
                    .fromJson(WorkPayload::class.java, JacksonJsonObjectMapper())
                    .apply { setTrustedPackages(WorkPayload::class.java.packageName) },
            ).handle(
                workHandler(),
            ).get()

    /**
     * Invoke the business service with the payload from the Message. payload.id =2 will throw an exception
     */
    private fun workHandler(): MessageHandler =
        MessageHandler { message: Message<*> ->
            // The payload was already transformed by the integration flow.
            val workPayload = message.payload as WorkPayload
            try {
                workService.doSomeWork(workPayload)
            } catch (e: RuntimeException) {
                throw AmqpRejectAndDontRequeueException(e)
            }
        }
}
