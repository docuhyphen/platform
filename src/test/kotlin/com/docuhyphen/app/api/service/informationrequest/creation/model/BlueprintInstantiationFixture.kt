package com.docuhyphen.app.api.service.informationrequest.creation.model

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestDocumentPlaceholder
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.creation.InformationRequestDocumentPlaceholderRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.blueprint.BlueprintDefinitionService
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestBlueprintInstantiationService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyMaterializationService

internal data class BlueprintInstantiationFixture(
    val service: InformationRequestBlueprintInstantiationService,
    val blueprintService: BlueprintDefinitionService,
    val requestRepository: InformationRequestRepository,
    val placeholderRepository: InformationRequestDocumentPlaceholderRepository,
    val partyMaterialization: InformationRequestPartyMaterializationService,
    val schemaAssignmentService: SchemaAssignmentService,
    val authorizationService: AuthorizationService,
    val entitlementGuard: InformationRequestEntitlementGuard,
    val savedRequests: MutableList<InformationRequest>,
    val savedPlaceholders: MutableList<InformationRequestDocumentPlaceholder>,
)
