package com.monowai.broker.model

/**
 * A simple payload contract to support tracking across service boundaries or in an async world.
 */
interface Payload<T> {
    val id: String // Correlation ID provided by the original caller
    val body: T // Business data
}
