package com.docuhyphen.app.api.model.audit

import java.util.*

data class AuditOrganizationAccess(
    val actor: AuditAccessActor,
    val organizationId: UUID,
)

data class AuditAccessDenial(
    val actor: AuditAccessActor,
    val organizationId: UUID?,
    val reasonCode: String,
)

data class AuditWorkflowDefinitionAccess(
    val actor: AuditAccessActor,
    val organizationId: UUID?,
    val platformOnly: Boolean,
)
