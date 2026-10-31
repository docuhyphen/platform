package com.docuhyphen.app.api.service.exchange.model

import java.util.*

data class BlueprintStartOwner(
    val userId: UUID,
    val recipientId: UUID,
    val organizationId: UUID?,
)

data class BlueprintStartTemplate(
    val versionId: UUID,
    val fieldDefinitionId: UUID,
    val fieldContractId: UUID,
)

data class StartedRequestRow(
    val id: UUID,
    val state: String,
    val templateVersionId: UUID,
    val ownerOrganizationId: UUID?,
    val ownerUserId: UUID?,
)
