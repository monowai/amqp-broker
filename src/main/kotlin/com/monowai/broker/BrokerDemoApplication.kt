package com.monowai.broker

import com.monowai.broker.integration.WorkPublisher
import com.monowai.broker.model.WorkPayload
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.io.BufferedReader
import java.io.InputStreamReader

@SpringBootApplication
class BrokerDemoApplication {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val ctx = runApplication<BrokerDemoApplication>(*args)
            // Production mode - Publish to Rabbit
            val workPublisher: WorkPublisher.WorkGateway = ctx.getBean(WorkPublisher.WorkGateway::class.java)
            var i = 1
            val reader = BufferedReader(InputStreamReader(System.`in`))
            var input: String?
            do {
                while (i++ < 3) {
                    workPublisher.publish(WorkPayload(i.toString(), "Hello World"))
                }
                i = 1
                input = reader.readLine()
            } while (input == null || !input.toString().toLowerCase().startsWith("q"))
            ctx.close()
        }
    }
}
