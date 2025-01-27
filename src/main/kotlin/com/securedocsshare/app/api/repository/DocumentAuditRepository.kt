package com.securedocsshare.app.api.repository

import com.securedocsshare.app.api.model.DocumentAuditLog
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class DocumentAuditLogRepository : BaseRepository<DocumentAuditLog>(DocumentAuditLog::class.java)