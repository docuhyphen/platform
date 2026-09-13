package com.docuhyphen.app.api.model.entity

/**
 * Which frozen capacity on a [RequestExecutionGrant] a [RequestExecutionUsageReservation] draws
 * against. Upload and storage kinds are deliberately absent: the grant carries no cap for either
 * yet, since no upload or storage capability exists anywhere in the platform to size one against.
 */
enum class RequestExecutionUsageKind
{
    ADDITIONAL_RECIPIENT,
}
