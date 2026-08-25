package com.monowai.broker.integration

import com.monowai.broker.integration.IncidentPublisher.IncidentGateway
import com.monowai.broker.model.IncidentPayload
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer

/**
 * The single place a processing failure is handled.
 *
 * * Park the message on the DLQ so it can be replayed
 * * Notify the Incident service that work was lost
 */
class DemoRepublishMessageRecoverer(
    errorTemplate: AmqpTemplate,
    errorExchange: String,
    errorRoutingKey: String,
    private val incidentGateway: IncidentGateway,
) : RepublishMessageRecoverer(errorTemplate, errorExchange, errorRoutingKey) {
    override fun recover(
        message: Message,
        cause: Throwable,
    ) {
        val rootCause = getCause(cause)
        // The DLQ is the system of record for the failed message - park it there first.
        super.recover(message, rootCause)
        // ...then tell someone. Losing the notification must not lose the message.
        raiseIncident(message, rootCause)
    }

    private fun raiseIncident(
        message: Message,
        cause: Throwable,
    ) {
        val correlationId = correlationId(message)
        try {
            incidentGateway.publish(
                IncidentPayload(correlationId, cause.message ?: cause.javaClass.simpleName),
            )
        } catch (e: RuntimeException) {
            logger.error("Unable to raise an incident for $correlationId", e)
        }
    }

    /**
     * The publisher stamps Payload.id onto the AMQP correlationId, so failure handling never has to
     * deserialise the body to work out which unit of work broke.
     */
    private fun correlationId(message: Message): String = message.messageProperties.correlationId ?: "unknown"

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
