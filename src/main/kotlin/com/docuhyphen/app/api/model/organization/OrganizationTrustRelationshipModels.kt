package com.docuhyphen.app.api.model.organization

import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import java.util.UUID

data class OrganizationTrustRequestOutcome(
    val relationship: OrganizationTrustRelationship,
    val eventType: AuditEventType,
)

internal data class CanonicalOrganizations(val organizationAId: UUID, val organizationBId: UUID)
