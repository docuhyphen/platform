package com.docuhyphen.app.api.model.entity

/**
 * Which frozen capacity on a [RequestExecutionGrant] a [RequestExecutionUsageReservation] draws
 * against. Evidence allowances are measured against what the request stores, so they take no
 * reservation.
 */
enum class RequestExecutionUsageKind
{
    ACTING_PARTY,
}
