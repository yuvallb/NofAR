package com.nofar.core.model

enum class LocationAccessState {
    NOT_REQUESTED,
    GRANTED,
    DENIED,
    DENIED_PERMANENTLY,
    WAITING_FOR_FIX
}
