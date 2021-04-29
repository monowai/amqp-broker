package com.monowai.broker

import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer

class DemoRepublishMessageRecoverer(errorTemplate: AmqpTemplate?, errorExchange: String?, errorRoutingKey: String?) :
    RepublishMessageRecoverer(errorTemplate, errorExchange, errorRoutingKey) {
    override fun recover(message: Message?, cause: Throwable?) {
        super.recover(message, getCause(cause!!))
    }

    private fun getCause(cause: Throwable): Throwable {
        // Strips out AmqpListener exception to provide a cleaner stack trace in the DLQ
        if (cause.cause == null) {
            return cause
        }
        if (cause is AmqpRejectAndDontRequeueException) {
            return cause.cause!!
        }
        return getCause(cause.cause!!)
    }
}
