package com.monowai.broker.model

data class WorkPayload(override val id: String, override val body: String) : Payload<String> {

    // Object contract is for Mockito verification
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WorkPayload

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
