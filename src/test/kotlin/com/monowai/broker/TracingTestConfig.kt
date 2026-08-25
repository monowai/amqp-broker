package com.monowai.broker

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * Collects spans in memory instead of shipping them to a collector.
 *
 * Boot picks up any SpanProcessor bean. A SimpleSpanProcessor exports on span end rather than on a
 * five second batch timer, so a test can assert without sleeping or force-flushing.
 */
@TestConfiguration
class TracingTestConfig {
    @Bean
    fun inMemorySpanExporter(): InMemorySpanExporter = InMemorySpanExporter.create()

    @Bean
    fun testSpanProcessor(inMemorySpanExporter: InMemorySpanExporter): SpanProcessor = SimpleSpanProcessor.create(inMemorySpanExporter)
}
