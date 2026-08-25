## Overview

Often, in corporates, developers operate on seriously locked down equipment where it may not be
possible to install services like Docker on to their workstations - ho hum.

To support such cases, I wanted to provide a solid integration example that could

* Demonstrate resilient loose coupling between services
* Run and test with nothing installed - no Docker, no broker, no shared environment
* Testable integration flows including an AMQP message broker
* Support for distributed tracing

I used the QPID broker adapted from the Java 7 example in
this [JIRA post](https://issues.apache.org/jira/browse/QPID-7747?page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel&focusedCommentId=15971267#comment-15971267)
and updated to use the latest library versions

What is demonstrated here is common in orgs - Basic direct channel Pub/Sub with a custom Message
Recoverer to use as an advice handler.

* Try to process the message
* Handle a process failure
* Notify an Incident service that a failure has occurred (though you're probably better off
  monitoring the DLQ)

For simplicity, the publisher is also its own subscriber.

### Failure handling

`DemoRepublishMessageRecoverer` is the single place a processing failure is dealt with, and it does
two independent things:

| Route      | Carries                            | Why                                       |
|------------|------------------------------------|-------------------------------------------|
| `work-dlq` | The original message + stack trace | System of record. Replay it.              |
| `incident` | An `IncidentPayload` - id + reason | Lightweight "someone look at this" event. |

The DLQ is written **first** - a lost notification must never cost you the message. The incident
publish is best effort and is logged if it fails.

Neither route knows about the other. `IncidentService` never sees an AMQP type, only a `Payload`, so
it stays testable without a broker.

#### Two ways to dead-letter, and why this one

You need to choose your dead-letter approach. Ideally you will be monitoring DLQs to give you a
heads-up

|                   | App-level republish (used here)                                                                        | Broker-native DLX                                                                                       |
|-------------------|--------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| How               | `RepublishMessageRecoverer` on the listener's advice chain                                             | `x-dead-letter-exchange` / `x-dead-letter-routing-key` queue args, plus `defaultRequeueRejected: false` |
| Failure detail    | Adds `x-exception-message` and `x-exception-stacktrace` headers - you can see *why* from the DLQ alone | Adds `x-death` (count, queue, reason). No stack trace, no message                                       |
| Who does the work | Your process. If it dies mid-recover, the message is redelivered and retried                           | The broker. Works even if every consumer is gone                                                        |
| Coupling          | The app owns the error topology                                                                        | The topology owns it; the app just rejects                                                              |
| Extra hops        | A publish per failure                                                                                  | None                                                                                                    |

The stack trace is the reason this example republishes: for a *teaching* repo, being able to read
the cause off the dead-lettered message is worth more than the extra hop. A high-throughput service
with a well-run platform team is usually better off with a broker-native DLX and the stack trace in
its traces and logs instead - which, now that spans carry the error, this repo also gives you.

If you switch, the queue declaration becomes:

```kotlin
QueueBuilder.durable(WORK_ROUTE)
    .deadLetterExchange("demoExchange")
    .deadLetterRoutingKey(WORK_ROUTE_ERR)
    .build()
```

...and `workInterceptor` disappears entirely. Pick deliberately.

#### Correlation without deserialising

`WorkPublisher` promotes `Payload.id` onto the AMQP `correlationId` header while the payload is
still a typed object:

```kotlin
integrationFlow<WorkGateway> {
    enrichHeaders { headerFunction<WorkPayload>(AmqpHeaders.CORRELATION_ID) { it.payload.id } }
    transform(ObjectToJsonTransformer())
    handle(outboundAdapter(amqpTemplate).exchangeName(primaryExchange.name).routingKey(WORK_ROUTE))
}
```

The recoverer then correlates the failure back to the original unit of work by reading
`message.messageProperties.correlationId` - it never has to parse a body that may well be the reason
things broke.

The use case calls for the Spring AMQP client to connect with QPID for Unit Tests and, for
completeness, RabbitMQ when running in "production mode". AMQP is a protocol and QPID/RabbitMQ are
brokers.

This guide is not meant to represent the only way to do things. You should carefully review your
integration requirements before settling on your exchange and queue approach and characteristics

### Microservice Characteristics Demonstrated

Every row below is something you can go and read in the code. "Lightweight message broker" used to be on this list -
it is a testing technique, not an architectural property, so it now lives under [QPID Broker](#qpid-broker).

| Characteristic | Where | What actually demonstrates it |
|---|---|---|
| **Loose coupling** | `service/`, `model/` | Neither package imports a single messaging type - no `Message`, no AMQP, no Spring Integration. `WorkService` takes a `WorkPayload` and can be tested with `WorkService().doSomeWork(…)` |
| **Depend on an interface, not infrastructure** | `WorkGateway`, `IncidentGateway` | Callers publish through an interface they own. No channel names, no broker types, no `AmqpTemplate` leaking into calling code |
| **Asynchronous and event driven** | `WorkPublisher` | `publish` returns as soon as the message is on the exchange. The caller never waits for the consumer, and does not know there is one |
| **Failure isolation** | `WorkSubscriber`, `DemoRepublishMessageRecoverer` | A message the service cannot process is rejected, dead-lettered and left behind. The consumer keeps consuming - one poison message does not stop the queue |
| **Independent reactions to one event** | `work-dlq` and `incident` routes | Two consumers react to the same failure for different reasons, and neither knows the other exists |
| **Observability across a process boundary** | `TracingTest` | W3C trace context rides the message. Publish, consume, dead-letter and incident are one trace, and it is asserted rather than assumed |
| **Correlation that survives the hop** | `Payload.id` → AMQP `correlationId` | A *business* id, distinct from the *transport* trace id. Failure handling reads it without deserialising a body that may be why things broke |
| **Independently testable** | `QpidBrokerConfig` | The entire topology runs in-process on an ephemeral port. No Docker, no shared environment, no port to collide over |

### What this example deliberately does not demonstrate

A reference implementation that does not name its own boundaries is a trap. These are the gaps, roughly in order of
how much they would bite you in production.

* **Independent deployability.** The defining property of a microservice, and this repo has exactly one deployable -
  the publisher is its own subscriber. Split it in two and `WorkPayload` stops being a shared Kotlin class and becomes
  a contract you have to version.
* **Idempotent consumers.** Acknowledgement is `AUTO` and delivery is at-least-once, so a connection drop between
  processing and ack means the message comes back and `WorkService` runs twice. Nothing here is idempotent. On a real
  system this matters more than most of the rows in the table above.
* **Schema evolution.** The JSON carries a `__TypeId__` header and the consumer trusts only its own package. Fine in
  one process; across two services that is a coupling to a Kotlin class name. Real systems version a schema instead.
* **Retry with backoff.** Deliberately `maxRetries(0)` - one attempt, then the DLQ. That is right for a validation
  failure, which will fail identically forever, and wrong for a transient one. Note that `WorkSubscriber` does not
  draw that distinction either: every `RuntimeException` becomes an `AmqpRejectAndDontRequeueException`, so a broker
  blip and a bad payload are treated the same. Telling them apart, and adding backoff for the first, both belong in
  `AmqpPlumbing.workInterceptor`.
* **Independent scaling.** `concurrentConsumers(1)` and default prefetch. Both are knobs a real consumer tunes against
  its own throughput and ordering requirements.
* **Data ownership.** There is no datastore, so "a database per service" is untouched.
* **Service discovery, API gateways, circuit breaking.** None of it. One broker address, from configuration.

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

### Flows

Flows are written with Spring Integration's **Kotlin DSL** (`integrationFlow { }`). Two things fall
out of that

**Handlers are typed.** The Java DSL leaves you casting:

```kotlin
MessageHandler { message -> val payload = message.payload as WorkPayload; … }   // unchecked
```

The Kotlin DSL does not:

```kotlin
handle<WorkPayload> { payload, _ -> workService.doSomeWork(payload) }
```

Change the transform above it and you get a compile error rather than a `ClassCastException` on the
first bad message.

**A flow starts from an interface.** `integrationFlow<WorkGateway> { … }` makes `WorkGateway` the
entry point, so callers inject an interface and Spring Integration generates the implementation:

```kotlin
interface WorkGateway {
    fun publish(workPayload: WorkPayload)
}
```

No channel bean to declare, no `defaultRequestChannel = "someString"` to keep in sync with it.

Gateway proxy only exists once Spring Integration has processed the flow bean, so anything built
*earlier* cannot inject it. `AmqpPlumbing.workInterceptor` needs the incident gateway and is built
first, so it takes an `ObjectProvider` and hands the recoverer a
`() -> IncidentGateway` to call on the first failure.

### Tracing

Vendor-neutral, via `spring-boot-starter-opentelemetry` - Micrometer Tracing over the OpenTelemetry
SDK, exported with OTLP. Nothing in this codebase names a backend.

The interesting part is not that spans exist, it is that **the whole failure fan-out is one trace**:

```
demoExchange/work send          PRODUCER  ok
└── work receive                CONSUMER  ERROR
    ├── demoExchange/work-dlq send        PRODUCER
    └── demoExchange/incident send        PRODUCER
        └── incident receive              CONSUMER
```

Three things make that work:

1. `spring.rabbitmq.template.observation-enabled` puts a W3C `traceparent` header on every outgoing
   message.
2. The Spring Integration DSL builds its **own** listener containers, so
   `spring.rabbitmq.listener.*` never reaches them. Each `configureContainer` block calls
   `setObservationEnabled(true)` explicitly. Miss this and the consumer starts a fresh trace while
   the publisher's is orphaned - the failure mode is silent.
3. Spring AMQP treats a recovered message as handled, so the consumer span would be green.
   `DemoRepublishMessageRecoverer`
   calls `tracer.currentSpan()?.error(cause)` to make it red.

Logs are correlated too - `logging.pattern.correlation` prints `[app,traceId,spanId]` on every line,
so a log search and a trace search meet in the middle.

#### Exporting

No endpoint is configured by default. Spans are still created and still correlate the logs; they are
just not shipped. Point them anywhere OTLP is spoken:

```bash
-e MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT=http://collector:4318/v1/traces
```

Metrics export is off - this demo is about traces, and a metrics registry retrying a collector
nobody started is noise.

### QPID Broker

An in-memory broker is a *testing technique*, not an architectural property - it is what makes the topology
independently testable, and it says nothing about the design of the services. That is why it is here and not in the
characteristics table.

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

#### QPID Dynamic Port

`spring.rabbitmq.port` is deliberately **not** set in `src/test/resources/application.yaml`. Two
builds on one machine, or a RabbitMQ you forgot to stop, would fight over a hard-coded port.

That leaves a chicken and egg problem - Spring needs the port to build a `ConnectionFactory`, but
the port is not known until the broker has bound. `QpidBrokerConfig` solves it with a
`DynamicPropertyRegistrar`, which Spring initialises ahead of ordinary singletons:

```kotlin
@Bean
fun qpidPortRegistrar(qpidMemoryBroker: QpidMemoryBroker): DynamicPropertyRegistrar =
    DynamicPropertyRegistrar { registry ->
        registry.add("spring.rabbitmq.port") { qpidMemoryBroker.port }
    }
```

Asking for the broker there forces it up first, and the bound port is published into the
`Environment` before anything tries to connect.

#### Shutting it down

The broker holds a port and a thread pool, so it implements `AutoCloseable` and `SmartLifecycle`.
The phase matters:
`SimpleMessageListenerContainer` stops on `Int.MAX_VALUE` and `CachingConnectionFactory` on
`Int.MIN_VALUE`, and phases stop highest first. Sitting on `Int.MAX_VALUE` takes the broker down
with the consumers. Stop it any later and a live consumer will try to reconnect to a closing port,
which is how you end up with `No Engine available` at the end of every green build.

You can run `BrokerDemoApplication` against a running instance of RabbitMQ. One of the key purposes
of this example is to demonstrate unit testing.

### Unit Testing

Seven scenarios, all against the in-memory broker - no external dependencies required.

Behaviour (`BrokerDemoApplicationTests`):

* `sendAndReceive` - work reaches the business service
* `sendFailureRoutesToDlq` - a failure lands the message on the DLQ **and** raises a correlated
  incident
* `successRaisesNoIncident` - the happy path stays quiet

Tracing (`TracingTest`) - these use the **real** `WorkService`, so the spans asserted on are the
spans production would produce. Tracing you do not assert on is tracing that quietly stops working:

* `traceContextCrossesTheBroker` - the consume span's parent is the publish span
* `failurePathStaysInOneTrace` - all five spans share one trace, and both failure routes hang off
  the failed consume
* `theFailedConsumeIsMarkedInError` - the consumer span is `ERROR` and carries the exception
* `theDeadLetteredMessageCarriesTheTrace` - the DLQ message has a `traceparent`, so a replay can
  rejoin the trace

Spans are collected with an `InMemorySpanExporter` wired in as a `SpanProcessor` bean - a
`SimpleSpanProcessor`, so export happens on span end instead of a five second batch timer and the
tests never sleep waiting for it.

User/Pass auth to the broker is using `PLAIN`.

### Docker

Multi-stage build, layered jar, non-root user:

```bash
docker build -t amqp-broker-demo .
docker run -i --rm --network host -e SPRING_RABBITMQ_HOST=localhost amqp-broker-demo
```

`-i` matters - the demo publishes a batch then waits on stdin. Without it stdin is at EOF, which the
app treats as
"quit". (It used to treat EOF as "keep going", and published in a tight loop forever.)

### CI

`.github/workflows/ci.yml` has two jobs:

* **build** - wrapper validation, `./gradlew build` (ktlint + the QPID tests). No services. That is
  the point of the in-memory broker.
* **rabbitmq** - builds the image and runs it against a real RabbitMQ 4, then asserts work was
  consumed *and* that the log line carries a populated traceId.

The second job exists because of a bug the first could never catch: this repo used to declare
transient non-exclusive queues, which QPID accepts and **RabbitMQ 4 refuses**
(`Feature transient_nonexcl_queues is deprecated`). The queues are durable now, and the smoke test
keeps them that way.

### Formatting

ktlint runs as part of `build`. To fix violations:

```bash
./gradlew ktlintFormat
```

### Running against Rabbit

Rabbit is assumed to be started. You can do this simply with Docker

```bash
docker container run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4-management-alpine
docker start rabbitmq
```

The queues are declared **durable** for this reason: RabbitMQ 4 deprecated transient non-exclusive
queues and refuses to declare them. QPID accepts them happily, so the unit tests will never tell
you.

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

### License

Licensed under the [Apache License 2.0](LICENSE) - copy it, adapt it, ship it,
including commercially. The point of a reference implementation is that you take
it apart and use the parts.

The QPID broker bootstrap is adapted from the Java 7 example on
[QPID-7747](https://issues.apache.org/jira/browse/QPID-7747), also Apache 2.0.
