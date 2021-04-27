## Overview

Often, in corporates, developers operate on seriously locked down equipment where it may not be possible to install services like Docker on to their workstations.  This can lead to building and testing against services at a distance which should be avoided at all costs. This can in turn cause challenges on how to effectively test, particularly if you're being guided to build microservices, and lead to an over reliance on external services or mocking.  

To support such cases, I wanted to provide a solid integration example that could 
* Demonstrate resilient loose coupling between services
* Pure Java
* Testable integration flows including an AMQP message broker

This project demonstrates a Pub+Sub example with advice handling for errors.  For simplicity, the publisher is also its own subscriber. 

The use case calls for the Spring AMQP client to connect with QPID for Unit Tests and, for completeness, RabbitMQ when running in "production mode".  Keep in mind that AMQP is a protocol and QPID/RabbitMQ are brokers.  It is not intended to test brokers.

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

### QPID Broker
In order to support multi-platform development and testing, the broker is created entirely programmatically.

```kotlin
// Create a broker instance
val broker = QpidMemoryBroker(9989, "guest", "guest")
```

This broker has been adapted from Java 7 example in this [JIRA post](https://issues.apache.org/jira/browse/QPID-7747?page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel&focusedCommentId=15971267#comment-15971267) and updated to use the latest library versions

### Running against Rabbit
```bash
docker container run -d --name rabbitmq -p 5672:5672 -p 15672:15672 -p 25672:25672 rabbitmq:3.8-management-alpine
docker start rabbitmq
```

### Notes on Unit Testing
* Spring will likely throw a connection refused exception when the QPID broker is shutting down.  This can be ignored
* User/Pass auth to the broker is using `PLAIN` 

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
