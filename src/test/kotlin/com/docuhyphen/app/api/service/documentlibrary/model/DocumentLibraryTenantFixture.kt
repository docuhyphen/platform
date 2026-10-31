package com.docuhyphen.app.api.service.documentlibrary.model

import com.docuhyphen.app.api.repository.documentlibrary.DocumentLibraryRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.documentlibrary.DocumentLibraryService
import com.docuhyphen.app.api.service.storage.FileStorageService

internal data class DocumentLibraryTenantFixture(
    val service: DocumentLibraryService,
    val repository: DocumentLibraryRepository,
    val storage: FileStorageService,
    val auditRecorder: AuditRecorder,
)
