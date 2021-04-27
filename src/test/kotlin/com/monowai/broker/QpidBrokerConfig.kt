package com.monowai.broker

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

@Component
class QpidBrokerConfig {

    @Bean
    fun getQpidBroker(
        @Value("\${spring.rabbitmq.port}") port: Int,
        @Value("\${spring.rabbitmq.username:guest}") username: String,
        @Value("\${spring.rabbitmq.password:guest}") password: String
    ): QpidMemoryBroker {
        return QpidMemoryBroker(port, username, password)
    }
}
