package com.monowai.broker

import org.apache.qpid.server.configuration.updater.TaskExecutor
import org.apache.qpid.server.configuration.updater.TaskExecutorImpl
import org.apache.qpid.server.logging.EventLogger
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
import org.springframework.context.SmartLifecycle
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/**
 * InMemory PLAIN auth QPID broker for unittesting.
 * Uses Java 25, Kotlin, Spring Boot and Spring Integration
 * Modified to use PLAIN auth as I couldn't be bothered to figure out harmony between Spring Rabbit and QPID auth schemes.
 *
 * Pass [EPHEMERAL_PORT] and let the OS pick - nothing then collides with a colleague, a CI agent
 * running two builds, or a RabbitMQ you forgot to stop. Read the real port back from [port].
 *
 * The broker owns a port and a thread pool, so it has to come down with the context. It is a
 * [SmartLifecycle] so that teardown is ordered against the AMQP client beans rather than left to
 * chance - see [getPhase]. [close] is idempotent and safe to call directly, so the broker also
 * works standalone in a `use { }` block.
 */
class QpidMemoryBroker(
    port: Int,
    userName: String,
    userPassword: String,
) : SmartLifecycle,
    AutoCloseable {
    private val amqpPort: AmqpPort<*>
    private val systemConfig: SystemConfig<*>
    private val taskExecutor: TaskExecutor
    private val running = AtomicBoolean(true)

    /**
     * The port the broker actually bound to. Only meaningful once the constructor has returned.
     */
    val port: Int
        get() = amqpPort.boundPort

    /**
     * Phases are stopped highest first. SimpleMessageListenerContainer sits on Int.MAX_VALUE and
     * CachingConnectionFactory on Int.MIN_VALUE, so going down with the listener containers means
     * the broker is gone before anything is left running to retry a connection to it.
     *
     * Leave this alone unless you enjoy reading "No Engine available" at the end of every build -
     * stopping the broker any later gives a live consumer a window to reconnect to a closing port.
     */
    override fun getPhase(): Int = Int.MAX_VALUE

    override fun isRunning(): Boolean = running.get()

    /** The constructor already bound the port - the Environment needed it before refresh. */
    override fun start() = Unit

    override fun stop() = close()

    override fun close() {
        if (!running.compareAndSet(true, false)) {
            return // Already stopped. Spring may call stop() and the destroy method.
        }
        val boundPort = port
        systemConfig.close()
        // The executor is ours, not the container's - the container will not stop it for us.
        taskExecutor.stop()
        logger.info("Stopped the in-memory QPID broker on port {}", boundPort)
    }

    companion object {
        /** Bind to whatever the OS has spare. */
        const val EPHEMERAL_PORT = 0

        private val logger = LoggerFactory.getLogger(QpidMemoryBroker::class.java)
    }

    init {
        Handler.register()
        taskExecutor = TaskExecutorImpl()
        val messageLogger: MessageLogger = LoggingMessageLogger()
        val eventLogger = EventLogger()
        eventLogger.messageLogger = messageLogger
        val configFactoryLoader: PluggableFactoryLoader<*> =
            PluggableFactoryLoader(
                SystemConfigFactory::class.java,
            )
        val configFactory: SystemConfigFactory<*> =
            configFactoryLoader[MemorySystemConfigImpl.SYSTEM_CONFIG_TYPE] as MemorySystemConfigImplFactory
        taskExecutor.start()
        val attributes: MutableMap<String, Any> = HashMap()
        attributes["initialConfigurationLocation"] =
            "data:;base64," +
            Base64.getEncoder().encodeToString(
                ("{\"name\": \"test\",\"modelVersion\":\"" + BrokerModel.MODEL_VERSION + "\"}").toByteArray(),
            )
        attributes["context"] =
            mapOf(
                Pair(
                    "qpid.broker.defaultPreferenceStoreAttributes",
                    "{\"type\": \"Noop\"}}",
                ),
            )
        val systemConfig =
            configFactory.newInstance(
                taskExecutor,
                eventLogger,
                { "system" },
                attributes,
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
        val authenticationProvider =
            broker.createChild(
                AuthenticationProvider::class.java,
                authenticationProviderAttributes,
            )

        // create user
        val userAttributes: MutableMap<String, Any> = HashMap()
        userAttributes[User.NAME] = userName
        userAttributes[User.PASSWORD] = userPassword
        authenticationProvider.createChild(User::class.java, userAttributes)

        // create amqp port. 0 means "any free port" - ask the broker what it got via QpidMemoryBroker.port
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
        this.systemConfig = systemConfig
        logger.info("Started the in-memory QPID broker on port {}", this.port)
    }
}
