package com.monowai.broker.integration

import com.monowai.broker.model.IncidentPayload

/**
 * The way in to the incident flow. See [WorkGateway].
 */
interface IncidentGateway {
    fun publish(incidentPayload: IncidentPayload)
}
