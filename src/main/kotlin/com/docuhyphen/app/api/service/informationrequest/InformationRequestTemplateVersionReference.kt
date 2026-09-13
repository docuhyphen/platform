package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import java.util.UUID

/**
 * One exact Information Request Template Version, named from outside the Information Request domain.
 *
 * The owner travels with the reference because holding a Version is the thing that decides whether
 * another record may name it. A caller that stores this reference is storing configuration that
 * belongs to that owner, and nothing else may put itself in the middle of that relationship.
 */
data class InformationRequestTemplateVersionReference(
    val templateVersionId: UUID,
    val templateDefinitionId: UUID,
    val versionNumber: Int,
    val ownerScopeKind: InformationRequestTemplateScopeKind,
    val ownerOrganizationId: UUID?,
    val ownerUserId: UUID?,
)

