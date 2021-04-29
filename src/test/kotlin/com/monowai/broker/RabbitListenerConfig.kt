package com.monowai.broker

import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitListenerConfig {
    val queueResults = mutableMapOf<String, Message?>()

    fun reset() {
        queueResults.clear()
    }

    @RabbitListener(id = "workDlq", queues = ["work-dlq"])
    fun workDlqMessage(message: Message) {
        queueResults["work-dlq"] = message
    }
}
