val qpidVersion = "10.1.0"
val kotlinVersion = "2.4.10"

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.monowai"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Keep the Spring Boot BOM in step with the Kotlin plugin above.
extra["kotlin.version"] = kotlinVersion

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-integration")
    implementation("org.springframework.integration:spring-integration-amqp")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Vendor-neutral tracing. Pulls Micrometer Tracing, the OpenTelemetry SDK and the OTLP
    // exporter. Point it at any OTLP collector - Tempo, Jaeger, Honeycomb, a vendor agent -
    // by setting one endpoint. Nothing here knows which.
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")

    // The QPID broker is only started by the tests; it is not part of the runtime application.
    testImplementation("org.apache.qpid:qpid-broker-core:$qpidVersion")
    testImplementation("org.apache.qpid:qpid-broker-plugins-memory-store:$qpidVersion")
    testImplementation("org.apache.qpid:qpid-broker-plugins-amqp-0-8-protocol:$qpidVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-amqp-test")
    testImplementation("org.springframework.integration:spring-integration-test")
    testImplementation("org.mockito:mockito-core")
    // InMemorySpanExporter - lets the tests assert on real spans instead of trusting config.
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

ktlint {
    version = "1.8.0"
}

tasks.withType<Test> {
    useJUnitPlatform()
}
