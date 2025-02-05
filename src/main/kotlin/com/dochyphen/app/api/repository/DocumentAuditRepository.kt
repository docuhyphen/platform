package com.dochyphen.app.api.repository

import com.dochyphen.app.api.model.DocumentAuditLog
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class DocumentAuditLogRepository : BaseRepository<DocumentAuditLog>(DocumentAuditLog::class.java)