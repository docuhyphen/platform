package com.docuhyphen.app.api.exception

import com.docuhyphen.app.api.model.subscription.SubscriptionDenial

class SubscriptionDenialException(val denial: SubscriptionDenial) : RuntimeException(denial.message)

