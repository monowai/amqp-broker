package com.monowai.broker.integration

import com.monowai.broker.model.WorkPayload

/**
 * The way in to the publishing flow.
 *
 * Callers depend on this interface - not on AMQP, not on a channel bean name. Spring Integration
 * generates the implementation from the flow that declares it, so there is no string to typo and
 * nothing to keep in sync.
 */
interface WorkGateway {
    fun publish(workPayload: WorkPayload)
}
