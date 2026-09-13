package com.docuhyphen.app.api.service.subscription

/**
 * Why a commercial plan check refused an operation.
 *
 * These are distinct from authorization failures: the caller may hold every required role and
 * still be refused because the paying subject does not own the feature, has exhausted an
 * allowance, or is no longer in a state that permits changes.
 */
enum class SubscriptionDenialReason
{
    FEATURE_NOT_INCLUDED,
    PLAN_LIMIT_REACHED,
    SUBSCRIPTION_PAST_DUE,
    SUBSCRIPTION_SUSPENDED,
    SUBSCRIPTION_CANCELED,
    SEAT_LIMIT_REACHED,
    ORGANIZATION_SUBSCRIPTION_REQUIRED,
    FEATURE_NOT_RELEASED,
}

