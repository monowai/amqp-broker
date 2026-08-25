## Overview

Often, in corporates, developers operate on seriously locked down equipment where it may not be possible to install
services like Docker on to their workstations - ho hum.

To support such cases, I wanted to provide a solid integration example that could

* Demonstrate resilient loose coupling between services
* Pure Java implementation
* Testable integration flows including an AMQP message broker

I used the QPID broker adapted from the Java 7 example in
this [JIRA post](https://issues.apache.org/jira/browse/QPID-7747?page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel&focusedCommentId=15971267#comment-15971267)
and updated to use the latest library versions

What is demonstrated here is common in orgs - Basic direct channel Pub/Sub with a custom Message Recoverer to use as an
advice handler.

* Try to process the message
* Handle a process failure
* Notify an Incident service that a failure has occurred (though you're probably better off monitoring the DLQ)

For simplicity, the publisher is also its own subscriber.

### Failure handling

`DemoRepublishMessageRecoverer` is the single place a processing failure is dealt with, and it does two independent
things:

| Route      | Carries                             | Why                                       |
|------------|-------------------------------------|-------------------------------------------|
| `work-dlq` | The original message + stack trace  | System of record. Replay it.              |
| `incident` | An `IncidentPayload` - id + reason  | Lightweight "someone look at this" event. |

The DLQ is written **first** - a lost notification must never cost you the message. The incident publish is best
effort and is logged if it fails.

Neither route knows about the other. `IncidentService` never sees an AMQP type, only a `Payload`, so it stays
testable without a broker.

#### Correlation without deserialising

`WorkPublisher` promotes `Payload.id` onto the AMQP `correlationId` header while the payload is still a typed object:

```kotlin
IntegrationFlow.from(sendWork)
    .enrichHeaders { h -> h.headerFunction<WorkPayload>(AmqpHeaders.CORRELATION_ID) { m -> m.payload.id } }
    .transform(ObjectToJsonTransformer())
```

The recoverer then correlates the failure back to the original unit of work by reading
`message.messageProperties.correlationId` - it never has to parse a body that may well be the reason things broke.

The use case calls for the Spring AMQP client to connect with QPID for Unit Tests and, for completeness, RabbitMQ when
running in "production mode". AMQP is a protocol and QPID/RabbitMQ are brokers.

This guide is not meant to represent the only way to do things. You should carefully review your integration
requirements before settling on your exchange and queue approach and characteristics

### Microservice Characteristics Demonstrated

* Loose Coupling
* Lightweight Message Broker
* Independently Testable

### Stack

* Java 25 - virtual threads enabled (`spring.threads.virtual.enabled`)
* Kotlin 2.4
* Spring Boot 4.1 / Spring Integration 7.1 / Spring AMQP 4.1
* Apache QPID Broker-J 10 (Test)
* RabbitMQ (Run)
* Gradle 9, ktlint

### Structure

* Model - Simple domain model
* Service - integration agnostic business services.
* Integration - all AMPQ related classes

### QPID Broker

In order to support multi-platform development and testing, the broker is created programmatically.

```kotlin
// Standalone. EPHEMERAL_PORT (0) lets the OS pick; ask the broker what it got.
QpidMemoryBroker(QpidMemoryBroker.EPHEMERAL_PORT, "guest", "guest").use { broker ->
    println("listening on ${broker.port}")
}
```

Under Spring, import `QpidBrokerConfig` and the port takes care of itself:

```kotlin
@SpringBootTest
@Import(QpidBrokerConfig::class)
class MyTest {
    @Autowired
    lateinit var qpidMemoryBroker: QpidMemoryBroker
}
```

#### Nothing pins a port

`spring.rabbitmq.port` is deliberately **not** set in `src/test/resources/application.yaml`. Two builds on one machine,
or a RabbitMQ you forgot to stop, would fight over a hard-coded port.

That leaves a chicken and egg problem - Spring needs the port to build a `ConnectionFactory`, but the port is not known
until the broker has bound. `QpidBrokerConfig` solves it with a `DynamicPropertyRegistrar`, which Spring initialises
ahead of ordinary singletons:

```kotlin
@Bean
fun qpidPortRegistrar(qpidMemoryBroker: QpidMemoryBroker): DynamicPropertyRegistrar =
    DynamicPropertyRegistrar { registry ->
        registry.add("spring.rabbitmq.port") { qpidMemoryBroker.port }
    }
```

Asking for the broker there forces it up first, and the bound port is published into the `Environment` before anything
tries to connect.

#### Shutting it down

The broker holds a port and a thread pool, so it implements `AutoCloseable` and `SmartLifecycle`. The phase matters:
`SimpleMessageListenerContainer` stops on `Int.MAX_VALUE` and `CachingConnectionFactory` on `Int.MIN_VALUE`, and phases
stop highest first. Sitting on `Int.MAX_VALUE` takes the broker down with the consumers. Stop it any later and a live
consumer will try to reconnect to a closing port, which is how you end up with `No Engine available` at the end of
every green build.

You can run `BrokerDemoApplication` against a running instance of RabbitMQ. One of the key purposes of this example is
to demonstrate unit testing.

### Unit Testing

Three test scenarios, all against the in-memory broker - no Docker:

* `sendAndReceive` - work reaches the business service
* `sendFailureRoutesToDlq` - a failure lands the message on the DLQ **and** raises a correlated incident
* `successRaisesNoIncident` - the happy path stays quiet

User/Pass auth to the broker is using `PLAIN`.

### Formatting

ktlint runs as part of `build`. To fix violations:

```bash
./gradlew ktlintFormat
```

### Running against Rabbit

Rabbit is assumed to be started. You can do this simply with Docker

```bash
docker container run -d --name rabbitmq -p 5672:5672 -p 15672:15672 -p 25672:25672 rabbitmq:4-management-alpine
docker start rabbitmq
```

### Further Reading

* [Spring Integration Examples](https://github.com/spring-projects/spring-integration-samples)
* [RabbitMQ interoperability matrix](https://www.rabbitmq.com/interoperability.html)
* [Test Containers RabbitMQ](https://www.testcontainers.org/modules/rabbitmq)
* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/gradle-plugin/index.html)
* [Spring Integration AMQP Module Reference Guide](https://docs.spring.io/spring-integration/reference/amqp.html)
* [Spring Integration Test Module Reference Guide](https://docs.spring.io/spring-integration/reference/testing.html)
* [Spring Integration](https://docs.spring.io/spring-boot/reference/messaging/spring-integration.html)
* [Spring for RabbitMQ](https://docs.spring.io/spring-boot/reference/messaging/amqp.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Integrating Data](https://spring.io/guides/gs/integration/)
* [Messaging with RabbitMQ](https://spring.io/guides/gs/messaging-rabbitmq/)
