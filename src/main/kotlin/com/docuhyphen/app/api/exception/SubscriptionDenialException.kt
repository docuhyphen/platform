package com.docuhyphen.app.api.exception

import com.docuhyphen.app.api.service.subscription.SubscriptionDenial

/**
 * Raised when a commercial plan check refuses an operation while enforcement is active.
 *
 * The carried [denial] describes the plan, the feature, and the allowance involved so the API
 * can explain the current allowance and the plan that lifts it instead of reporting a generic
 * authorization failure.
 */
class SubscriptionDenialException(val denial: SubscriptionDenial) : RuntimeException(denial.message)

