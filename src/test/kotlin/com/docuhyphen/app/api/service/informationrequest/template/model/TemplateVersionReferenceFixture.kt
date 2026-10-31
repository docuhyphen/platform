package com.docuhyphen.app.api.service.informationrequest.template.model

import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateReferenceService

internal data class TemplateVersionReferenceFixture(
    val service: InformationRequestTemplateReferenceService,
    val definitionRepository: InformationRequestTemplateDefinitionRepository,
    val versionRepository: InformationRequestTemplateVersionRepository,
    val entitlementGuard: InformationRequestTemplateEntitlementGuard,
)
