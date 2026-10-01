package com.docuhyphen.app.api.model.informationrequest.creation

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import java.util.UUID

data class CreateAdHocInformationRequestCommand(
    val exchangeId: UUID,
    val displayName: String,
    val description: String? = null,
    val configuration: InformationRequestTemplateConfigurationRequest,
    val gatesExchangeClosure: Boolean = true,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class InformationRequestCreationResult(
    val request: InformationRequest,
    val requestETag: String,
    val requirementCount: Int,
)

data class CreateInformationRequestFromBlueprintCommand(
    val blueprintDefinitionId: UUID,
    val exchangeId: UUID,
    val gatesExchangeClosure: Boolean = true,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class BlueprintInformationRequestPartyDefault(
    val roleKey: InformationRequestShareRoleKey,
    val principal: PrincipalRef,
)

data class CreateInformationRequestFromTemplateVersionCommand(
    val templateVersionId: UUID,
    val exchangeId: UUID,
    val gatesExchangeClosure: Boolean = true,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)
