package com.monowai.broker

import org.apache.qpid.server.configuration.updater.TaskExecutor
import org.apache.qpid.server.configuration.updater.TaskExecutorImpl
import org.apache.qpid.server.logging.LoggingMessageLogger
import org.apache.qpid.server.logging.MessageLogger
import org.apache.qpid.server.model.AuthenticationProvider
import org.apache.qpid.server.model.BrokerModel
import org.apache.qpid.server.model.Port
import org.apache.qpid.server.model.Protocol
import org.apache.qpid.server.model.SystemConfig
import org.apache.qpid.server.model.User
import org.apache.qpid.server.model.VirtualHostNode
import org.apache.qpid.server.model.port.AmqpPort
import org.apache.qpid.server.plugin.PluggableFactoryLoader
import org.apache.qpid.server.plugin.SystemConfigFactory
import org.apache.qpid.server.store.MemorySystemConfigImpl
import org.apache.qpid.server.store.MemorySystemConfigImplFactory
import org.apache.qpid.server.util.urlstreamhandler.data.Handler
import org.apache.qpid.server.virtualhostnode.memory.MemoryVirtualHostNode
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * InMemory PLAIN auth QPID broker for unittesting.
 * Uses Java 11, Kotlin, Spring Boot and Spring Integration
 * Modified to use PLAIN auth as I couldn't be bothered to figure out harmony between Spring Rabbit and QPID auth schemes.
 */
class QpidMemoryBroker(port: Int, userName: String, userPassword: String) {
    private val amqpPort: AmqpPort<*>
    private val systemConfig: SystemConfig<*>
    val port: Int
        get() = amqpPort.boundPort

    fun close() {
        systemConfig.close()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(QpidMemoryBroker::class.java)
    }

    init {
        Handler.register()
        val taskExecutor: TaskExecutor = TaskExecutorImpl()
        val messageLogger: MessageLogger = LoggingMessageLogger()
        val eventLogger = org.apache.qpid.server.logging.EventLogger()
        eventLogger.messageLogger = messageLogger
        val configFactoryLoader: PluggableFactoryLoader<*> = PluggableFactoryLoader(
            SystemConfigFactory::class.java
        )
        val configFactory: SystemConfigFactory<*> =
            configFactoryLoader[MemorySystemConfigImpl.SYSTEM_CONFIG_TYPE] as MemorySystemConfigImplFactory
        taskExecutor.start()
        val attributes: MutableMap<String, Any> = HashMap()
        attributes["initialConfigurationLocation"] = "data:;base64," + Base64.getEncoder().encodeToString(
            ("{\"name\": \"test\",\"modelVersion\":\"" + BrokerModel.MODEL_VERSION + "\"}").toByteArray()
        )
        attributes["context"] = mapOf(
            Pair(
                "qpid.broker.defaultPreferenceStoreAttributes",
                "{\"type\": \"Noop\"}}"
            )
        )
        val systemConfig = configFactory.newInstance(
            taskExecutor,
            eventLogger,
            { "system" },
            attributes
        )
        systemConfig.open()

        // get containing broker
        val broker = systemConfig.container

        // create appropriate authentication provider
        val authenticationProviderAttributes: MutableMap<String, Any> = HashMap()

        authenticationProviderAttributes[AuthenticationProvider.TYPE] = "Plain"
        // authenticationProviderAttributes[AuthenticationProvider.TYPE] = ScramSHA256AuthenticationManager.PROVIDER_TYPE
        authenticationProviderAttributes["secureOnlyMechanisms"] = arrayListOf("")
        authenticationProviderAttributes[AuthenticationProvider.NAME] = "auth"
        val authenticationProvider = broker.createChild(
            AuthenticationProvider::class.java,
            authenticationProviderAttributes
        )

        // create user
        val userAttributes: MutableMap<String, Any> = HashMap()
        userAttributes[User.NAME] = userName
        userAttributes[User.PASSWORD] = userPassword
        authenticationProvider.createChild(User::class.java, userAttributes)

        // create amqp port
        val portAttributes: MutableMap<String, Any> = HashMap()
        portAttributes[Port.NAME] = "amqp"
        portAttributes[Port.PORT] = port
        portAttributes[Port.AUTHENTICATION_PROVIDER] = authenticationProvider.name
        portAttributes[Port.PROTOCOLS] = setOf(Protocol.AMQP_0_9_1)
        amqpPort = broker.createChild(Port::class.java, portAttributes) as AmqpPort<*>

        // create virtual host node and virtual host
        val virtualHostNodeAttributes: MutableMap<String, Any> = HashMap()
        virtualHostNodeAttributes[VirtualHostNode.NAME] = "test"
        virtualHostNodeAttributes[VirtualHostNode.TYPE] = MemoryVirtualHostNode.VIRTUAL_HOST_NODE_TYPE
        virtualHostNodeAttributes["virtualHostInitialConfiguration"] = (
            "{\"name\": \"test\",\"modelVersion\":\"" +
                BrokerModel.MODEL_VERSION +
                "\", \"type\": \"Memory\"}"
            )
        virtualHostNodeAttributes[VirtualHostNode.DEFAULT_VIRTUAL_HOST_NODE] = true
        broker.createChild(VirtualHostNode::class.java, virtualHostNodeAttributes)
        logger.info(amqpPort.toString())
        this.systemConfig = systemConfig
    }
}
