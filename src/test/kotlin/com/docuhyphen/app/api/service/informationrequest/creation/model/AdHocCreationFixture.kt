package com.docuhyphen.app.api.service.informationrequest.creation.model

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersionCapability
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestAdHocCreationService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer

internal data class AdHocCreationFixture(
    val service: InformationRequestAdHocCreationService,
    val definitionRepository: InformationRequestTemplateDefinitionRepository,
    val versionRepository: InformationRequestTemplateVersionRepository,
    val capabilityRepository: InformationRequestTemplateVersionCapabilityRepository,
    val requestRepository: InformationRequestRepository,
    val configurationWriter: InformationRequestTemplateConfigurationWriter,
    val materializer: InformationRequestTemplateMaterializer,
    val authorizationService: AuthorizationService,
    val transitionHistory: InformationRequestTransitionHistoryService,
    val savedDefinitions: MutableList<InformationRequestTemplateDefinition>,
    val savedVersions: MutableList<InformationRequestTemplateVersion>,
    val savedCapabilities: MutableList<InformationRequestTemplateVersionCapability>,
    val savedRequests: MutableList<InformationRequest>,
)
