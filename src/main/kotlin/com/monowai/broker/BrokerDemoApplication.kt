package com.monowai.broker

import com.monowai.broker.integration.WorkGateway
import com.monowai.broker.model.WorkPayload
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BrokerDemoApplication

private const val BATCH_SIZE = 3

/**
 * Publish a batch, then wait. Press enter for another batch, "q" to quit.
 */
fun main(args: Array<String>) {
    runApplication<BrokerDemoApplication>(*args).use { ctx ->
        val workGateway = ctx.getBean(WorkGateway::class.java)
        do {
            publishBatch(workGateway)
            // null means stdin reached EOF - a container started without `-i`, or a piped script that
            // ran out. Treat that as "quit". Reading it as "keep going" publishes in a tight loop
            // until someone notices the CPU graph.
            val input = readlnOrNull()
        } while (input != null && !input.trim().startsWith("q", ignoreCase = true))
    }
}

private fun publishBatch(workGateway: WorkGateway) {
    (1..BATCH_SIZE).forEach { workGateway.publish(WorkPayload(it.toString(), "Hello World")) }
    // One the service cannot process, so a run exercises the DLQ and the incident route. The
    // failure lives here, in the demo driver - WorkService just validates its input like any service.
    workGateway.publish(WorkPayload("no-body", ""))
}
