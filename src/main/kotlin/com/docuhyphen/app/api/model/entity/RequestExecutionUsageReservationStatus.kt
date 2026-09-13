package com.docuhyphen.app.api.model.entity

/**
 * Lifecycle of one [RequestExecutionUsageReservation]. RESERVED and CONSUMED both still count
 * against the grant's cap; RELEASED and ROLLED_BACK give the capacity back, the former for a
 * reservation abandoned before it was finalized, the latter for finalized usage undone afterward.
 */
enum class RequestExecutionUsageReservationStatus
{
    RESERVED,
    CONSUMED,
    RELEASED,
    ROLLED_BACK,
}
