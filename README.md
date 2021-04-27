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

The use case calls for the Spring AMQP client to connect with QPID for Unit Tests and, for completeness, RabbitMQ when
running in "production mode". AMQP is a protocol and QPID/RabbitMQ are brokers.

This guide is not meant to represent the only way to do things. You should carefully review your integration
requirements before settling on your exchange and queue approach and characteristics

### Microservice Characteristics Demonstrated

* Loose Coupling
* Lightweight Message Broker
* Independently Testable

### Stack

* Spring Boot
* Spring Integration
* Apache QPID Broker (Test)
* RabbitMQ (Run)
* Gradle

### Structure

* Model - Simple domain model
* Service - integration agnostic business services.
* Integration - all AMPQ related classes

### QPID Broker

In order to support multi-platform development and testing, the broker is created programmatically.

```kotlin
// Create a broker instance
val broker = QpidMemoryBroker(9989, "guest", "guest")
// If you're using Spring, props are read from the spring.rabbitmq.* props defined in your application.yaml
@Autowired
lateinit var qpidMemoryBroker: QpidMemoryBroker
```

You can run `BrokerDemoApplication` against a running instance of RabbitMQ. One of the key purposes of this example is
to demonstrate unit testing.

### Unit Testing

Two test scenarios - success and simulated integration failure.

* User/Pass auth to the broker is using `PLAIN`

### Running against Rabbit

Rabbit is assumed to be started. You can do this simply with Docker

```bash
docker container run -d --name rabbitmq -p 5672:5672 -p 15672:15672 -p 25672:25672 rabbitmq:3.8-management-alpine
docker start rabbitmq
```

### Further Reading

* [Spring Integration Examples](https://github.com/spring-projects/spring-integration-samples)
* [RabbitMQ interoperability matrix](https://www.rabbitmq.com/interoperability.html)
* [Test Containers RabbitMQ](https://www.testcontainers.org/modules/rabbitmq)
* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/2.4.5/gradle-plugin/reference/html/)
* [Spring Integration AMQP Module Reference Guide](https://docs.spring.io/spring-integration/reference/html/amqp.html)
* [Spring Integration Test Module Reference Guide](https://docs.spring.io/spring-integration/reference/html/testing.html)
* [Spring Integration](https://docs.spring.io/spring-boot/docs/2.4.5/reference/htmlsingle/#boot-features-integration)
* [Spring for RabbitMQ](https://docs.spring.io/spring-boot/docs/2.4.5/reference/htmlsingle/#boot-features-amqp)

### Guides
The following guides illustrate how to use some features concretely:

* [Integrating Data](https://spring.io/guides/gs/integration/)
* [Messaging with RabbitMQ](https://spring.io/guides/gs/messaging-rabbitmq/)
