package com.docuhyphen.app.api.model.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import java.util.*

data class InformationRequestTemplateVersionReference(
    val templateVersionId: UUID,
    val templateDefinitionId: UUID,
    val versionNumber: Int,
    val ownerScopeKind: InformationRequestTemplateScopeKind,
    val ownerOrganizationId: UUID?,
    val ownerUserId: UUID?,
)

