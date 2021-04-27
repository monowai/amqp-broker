package com.monowai.broker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest
class BrokerDemoApplicationTests {
    companion object {
        @JvmStatic
        val broker = QpidMemoryBroker(9989, "guest", "guest")
    }

    @Autowired
    lateinit var publisher: WorkPublisher.WorkGateway

    @MockBean
    lateinit var workService: WorkService

    @Test
    fun sendAndReceive() {
        assertThat(broker).isNotNull
        val first = WorkPayload("1","Test Payload")
        val second = WorkPayload("2","Test Payload")
        publisher.publish(first)
        publisher.publish(second)
        Thread.sleep(1000)
        broker.close()
        verify(workService, atLeast(1)).doSomeWork(first)
        verify(workService, atLeast(1)).doSomeWork(second)
    }
}
