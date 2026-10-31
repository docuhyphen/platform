package com.docuhyphen.app.api.service.communication.model

import com.docuhyphen.app.api.repository.communication.CommunicationRepository
import com.docuhyphen.app.api.service.communication.CommunicationService
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard

internal data class CommunicationTenantFixture(
    val service: CommunicationService,
    val repository: CommunicationRepository,
    val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
)
