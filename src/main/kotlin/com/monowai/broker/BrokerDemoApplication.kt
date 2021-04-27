package com.monowai.broker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BrokerDemoApplication {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val ctx = runApplication<BrokerDemoApplication>(*args)
            // Production mode - Publish to Rabbit
            val workPublisher: WorkPublisher.WorkGateway = ctx.getBean(WorkPublisher.WorkGateway::class.java)
            var i = 0
            while (i++ < 10) {
                workPublisher.publish(WorkPayload(i.toString(),"Hello World"))
            }
            ctx.close()
        }
    }
}
