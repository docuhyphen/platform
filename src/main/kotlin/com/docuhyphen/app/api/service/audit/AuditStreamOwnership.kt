package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import java.util.*

object AuditStreamOwnership {
    fun matches(streamId: String, ownerType: String, ownerId: UUID?): Boolean {
        val owner = runCatching { LedgerProcessor.ownerOfStream(streamId) }.getOrNull() ?: return false
        return when (owner) {
            AuditOwnerScope.Platform -> ownerType == "PLATFORM" && ownerId == null
            is AuditOwnerScope.Personal -> ownerType == "USER" && ownerId == owner.userId
            is AuditOwnerScope.Organization -> ownerType == "ORGANIZATION" && ownerId == owner.organizationId
        }
    }
}
