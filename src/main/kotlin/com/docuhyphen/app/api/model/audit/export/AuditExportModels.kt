package com.docuhyphen.app.api.model.audit.export

import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import java.time.Instant
import java.util.*

data class AuditExportRequest(
    val organizationId: UUID?,
    val ownerUserId: UUID? = null,
    val categories: Set<AuditCategory>,
    val occurredAfter: Instant,
    val occurredBefore: Instant,
    val purpose: String,
    val caseReference: String? = null,
    val legalBasis: String? = null,
    val downloadLimit: Int? = null,
)
