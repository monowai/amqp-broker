package com.monowai.broker.model

/**
 * Raised when a unit of work could not be processed.
 *
 * The id correlates the incident back to the WorkPayload that failed, so the Incident service
 * never needs to see the original message - only that it broke, and why.
 */
data class IncidentPayload(
    override val id: String,
    override val body: String,
) : Payload<String>
