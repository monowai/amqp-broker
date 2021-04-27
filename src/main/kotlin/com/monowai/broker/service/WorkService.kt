package com.monowai.broker.service

import com.monowai.broker.model.WorkPayload
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Implement your business logic into a service. This class will have nothing to do with "messaging"
 */
@Service
class WorkService {
    companion object {
        val log: Logger = LoggerFactory.getLogger("WorkService")
    }

    fun doSomeWork(workPayload: WorkPayload) {
        log.info("id ${workPayload.id} says ${workPayload.body}")
        if (workPayload.id == "2") {
            throw RuntimeException("Psuedo Error")
        }
    }
}
