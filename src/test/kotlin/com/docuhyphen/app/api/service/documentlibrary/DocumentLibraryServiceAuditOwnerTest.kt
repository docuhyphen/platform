package com.docuhyphen.app.api.service.documentlibrary

import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.model.entity.DocumentLibraryEntry
import com.docuhyphen.app.api.repository.documentlibrary.DocumentLibraryRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.storage.FileStorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.UUID

/**
 * A personally owned Document Library entry must file its download event under its real
 * [AuditOwnerScope.Personal] owner. Before this fix, only [BlueprintScope.ORG] resolved to an
 * organization owner and every other scope, including a personal entry, silently fell back to
 * [AuditOwnerScope.Platform].
 */
class DocumentLibraryServiceAuditOwnerTest
{
    private fun service(
        repository: DocumentLibraryRepository = mock(),
        fileStorageService: FileStorageService = mock(),
        authorizationContextFactory: AuthorizationContextFactory = mock(),
        auditRecorder: AuditRecorder = mock<AuditRecorder>().also {
            whenever(it.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
        },
    ): DocumentLibraryService = DocumentLibraryService(
        repository = repository,
        fileStorageService = fileStorageService,
        authorizationService = mock<AuthorizationService>(),
        authorizationContextFactory = authorizationContextFactory,
        userRoleService = mock<UserRoleService>(),
        auditRecorder = auditRecorder,
        documentLibrarySubscriptionGuard = mock<DocumentLibrarySubscriptionGuard>(),
    )

    @Test
    fun `downloadFile resolves a personally owned entry to a Personal audit owner, never Platform`()
    {
        val ownerUserId = UUID.randomUUID()
        val entry = DocumentLibraryEntry().apply {
            title = "Personal Reference.pdf"
            scope = BlueprintScope.PERSONAL
            createdByAppUserId = ownerUserId
            storagePath = "lib/${id}.pdf"
        }
        val repository = mock<DocumentLibraryRepository>()
        whenever(repository.findById(entry.id)).thenReturn(entry)

        val authorizationContextFactory = mock<AuthorizationContextFactory>()
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(PrincipalRef.user(ownerUserId))
        whenever(authorizationContextFactory.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)

        val fileStorageService = mock<FileStorageService>()
        whenever(fileStorageService.downloadDocument(entry.storagePath!!)).thenReturn(File("irrelevant"))

        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))

        service(
            repository = repository,
            fileStorageService = fileStorageService,
            authorizationContextFactory = authorizationContextFactory,
            auditRecorder = auditRecorder,
        ).downloadFile(entry.id)

        val draftCaptor = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(draftCaptor.capture())
        assertEquals(AuditOwnerScope.Personal(ownerUserId), draftCaptor.firstValue.owner)
    }
}


