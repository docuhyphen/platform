package com.docuhyphen.app.api.service.informationrequest.creation.model

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestTemplateInstantiationService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer

internal data class TemplateInstantiationFixture(
    val service: InformationRequestTemplateInstantiationService,
    val exchange: Exchange,
    val requestRepository: InformationRequestRepository,
    val materializer: InformationRequestTemplateMaterializer,
    val entitlementGuard: InformationRequestEntitlementGuard,
    val transitionHistory: InformationRequestTransitionHistoryService,
    val saved: MutableList<InformationRequest>,
)
