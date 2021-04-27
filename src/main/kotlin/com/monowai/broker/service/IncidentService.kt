package com.monowai.broker.service

import com.monowai.broker.model.Payload
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class IncidentService {
    companion object {
        val log: Logger = LoggerFactory.getLogger("IncidentService")
    }

    fun raiseIncident(payload: Payload<*>) {
        log.info("An incident with the ID of ${payload.id} has occurred")
    }
}
