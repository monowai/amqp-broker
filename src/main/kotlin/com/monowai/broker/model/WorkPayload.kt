package com.monowai.broker.model

data class WorkPayload(
    override val id: String,
    override val body: String,
) : Payload<String>
