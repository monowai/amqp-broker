package com.monowai.broker.service

/**
 * The work cannot be done and retrying will not help. Route it to the DLQ, do not requeue it.
 */
class UnprocessableWorkException(
    id: String,
) : RuntimeException("Work $id has no body to process")
