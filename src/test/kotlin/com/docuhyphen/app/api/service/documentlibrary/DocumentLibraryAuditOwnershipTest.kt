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
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.storage.FileStorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.util.UUID

class DocumentLibraryAuditOwnershipTest
{
    @Test
    fun `app library download remains platform-owned when the session has an active organization`(@TempDir tempDir: Path)
    {
        val draft = downloadAndCapture(BlueprintScope.APP, null, tempDir)
        assertEquals(AuditOwnerScope.Platform, draft.owner)
    }

    @Test
    fun `organization library download uses the entry organization rather than the active session organization`(@TempDir tempDir: Path)
    {
        val organizationId = UUID.randomUUID()
        val draft = downloadAndCapture(BlueprintScope.ORG, organizationId, tempDir)
        assertEquals(AuditOwnerScope.Organization(organizationId), draft.owner)
    }

    private fun downloadAndCapture(
        scope: BlueprintScope,
        organizationId: UUID?,
        tempDir: Path,
    ): AuditEventDraft
    {
        val principal = PrincipalRef.user(UUID.randomUUID())
        val entry = DocumentLibraryEntry().apply {
            title = "Evidence template"
            this.scope = scope
            this.organizationId = organizationId
            storagePath = "lib/$id.pdf"
        }
        val repository = mock<DocumentLibraryRepository>()
        whenever(repository.findById(entry.id)).thenReturn(entry)
        val storage = mock<FileStorageService>()
        val file = tempDir.resolve("document.pdf").toFile().apply { writeText("content") }
        whenever(storage.downloadDocument(entry.storagePath!!)).thenReturn(file)
        val contextFactory = mock<AuthorizationContextFactory>()
        whenever(contextFactory.currentPrincipal()).thenReturn(principal)
        whenever(contextFactory.currentContext()).thenReturn(
            AuthorizationContext(activeOrgId = UUID.randomUUID()),
        )
        val userRoleService = mock<UserRoleService>()
        whenever(userRoleService.isAppAdmin(principal.id)).thenReturn(false)
        val authorizationService = mock<AuthorizationService>()
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        val recorder = mock<AuditRecorder>()
        whenever(recorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
        val service = DocumentLibraryService(
            repository,
            storage,
            authorizationService,
            contextFactory,
            userRoleService,
            recorder,
            mock<DocumentLibrarySubscriptionGuard>(),
        )

        service.downloadFile(entry.id)

        val captor = argumentCaptor<AuditEventDraft>()
        verify(recorder).record(captor.capture())
        return captor.firstValue
    }
}
