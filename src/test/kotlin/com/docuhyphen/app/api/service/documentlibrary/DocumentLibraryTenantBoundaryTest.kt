package com.docuhyphen.app.api.service.documentlibrary

import com.docuhyphen.app.api.model.dto.CloneDocumentLibraryEntryRequest
import com.docuhyphen.app.api.model.dto.CreateDocumentLibraryEntryRequest
import com.docuhyphen.app.api.model.dto.PatchDocumentLibraryPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchDocumentLibraryStatusRequest
import com.docuhyphen.app.api.model.dto.UpdateDocumentLibraryEntryRequest
import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.model.entity.DocumentLibraryEntry
import com.docuhyphen.app.api.repository.DocumentLibraryRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.storage.FileStorageService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.File
import java.util.UUID

class DocumentLibraryTenantBoundaryTest
{
    private val principalId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val principal = PrincipalRef.user(principalId)

    private fun authorizationContext(activeOrgId: UUID? = organizationId) =
        AuthorizationContext(activeOrgId = activeOrgId)

    private fun contextFactory(context: AuthorizationContext = authorizationContext()): AuthorizationContextFactory =
        mock<AuthorizationContextFactory>().also {
            whenever(it.currentPrincipal()).thenReturn(principal)
            whenever(it.currentContext()).thenReturn(context)
        }

    private fun roleService(
        appAdmin: Boolean,
        orgAdmin: Boolean,
    ): UserRoleService =
        mock<UserRoleService>().also {
            whenever(it.isAppAdmin(any())).thenReturn(appAdmin)
            whenever(it.isOrgAdminIn(any(), any())).thenReturn(orgAdmin)
        }

    private fun authorizationService(vararg allowedActions: Action): AuthorizationService =
        mock<AuthorizationService>().also {
            whenever(it.authorize(any(), any(), any(), any())).thenAnswer { invocation ->
                val action = invocation.getArgument<Action>(1)
                if (action in allowedActions)
                    Decision.Allow()
                else
                    Decision.Deny("test-deny", "Denied in test")
            }
        }

    private data class ServiceFixture(
        val service: DocumentLibraryService,
        val repository: DocumentLibraryRepository,
        val storage: FileStorageService,
        val auditRecorder: AuditRecorder,
    )

    private fun fixture(
        appAdmin: Boolean = true,
        orgAdmin: Boolean = false,
        allowedActions: Array<out Action> = emptyArray(),
        context: AuthorizationContext = authorizationContext(),
    ): ServiceFixture
    {
        val repository = mock<DocumentLibraryRepository>()
        val storage = mock<FileStorageService>()
        val auditRecorder = mock<AuditRecorder>()
        return ServiceFixture(
            service = DocumentLibraryService(
                repository = repository,
                fileStorageService = storage,
                authorizationService = authorizationService(*allowedActions),
                authorizationContextFactory = contextFactory(context),
                userRoleService = roleService(appAdmin, orgAdmin),
                auditRecorder = auditRecorder,
                documentLibrarySubscriptionGuard = mock<DocumentLibrarySubscriptionGuard>(),
            ),
            repository = repository,
            storage = storage,
            auditRecorder = auditRecorder,
        )
    }

    private fun entry(
        scope: BlueprintScope,
        createdBy: UUID = principalId,
    ) = DocumentLibraryEntry().apply {
        this.scope = scope
        this.createdByAppUserId = createdBy
        this.organizationId = if (scope == BlueprintScope.ORG) organizationId else null
        this.title = "Boundary Document"
        this.storagePath = "lib/$id.pdf"
    }

    @Test
    fun `APP_ADMIN does not receive unpublished organization documents in list query`()
    {
        val fixture = fixture(appAdmin = true, orgAdmin = false)
        whenever(
            fixture.repository.findAllAccessibleForCaller(principalId, organizationId, false)
        ).thenReturn(emptyList())

        assertEquals(emptyList<Any>(), fixture.service.listEntries(null, null))
        verify(fixture.repository).findAllAccessibleForCaller(principalId, organizationId, false)
    }

    @Test
    fun `APP_ADMIN cannot read another user's personal library entry`()
    {
        val fixture = fixture()
        val entry = entry(BlueprintScope.PERSONAL, UUID.randomUUID())
        whenever(fixture.repository.findById(entry.id)).thenReturn(entry)

        assertThrows<ForbiddenException> {
            fixture.service.getEntry(entry.id)
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot read organization library entry`()
    {
        val fixture = fixture()
        val entry = entry(BlueprintScope.ORG)
        whenever(fixture.repository.findById(entry.id)).thenReturn(entry)

        assertThrows<ForbiddenException> {
            fixture.service.getEntry(entry.id)
        }
    }

    @Test
    fun `dual-role APP_ADMIN reads organization library entry through organization permission`()
    {
        val fixture = fixture(
            appAdmin = true,
            orgAdmin = true,
            allowedActions = arrayOf(Action.DOC_LIBRARY_VIEW),
        )
        val entry = entry(BlueprintScope.ORG)
        whenever(fixture.repository.findById(entry.id)).thenReturn(entry)

        val result = fixture.service.getEntry(entry.id)

        assertEquals(entry.id, result.id)
    }

    @Test
    fun `APP_ADMIN cannot update another user's personal library entry`()
    {
        val fixture = fixture()
        val entry = entry(BlueprintScope.PERSONAL, UUID.randomUUID())
        whenever(fixture.repository.findById(entry.id)).thenReturn(entry)

        assertThrows<ForbiddenException> {
            fixture.service.updateEntry(
                entry.id,
                UpdateDocumentLibraryEntryRequest(title = "Changed"),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot update organization library entry`()
    {
        val fixture = fixture()
        val entry = entry(BlueprintScope.ORG)
        whenever(fixture.repository.findById(entry.id)).thenReturn(entry)

        assertThrows<ForbiddenException> {
            fixture.service.updateEntry(
                entry.id,
                UpdateDocumentLibraryEntryRequest(title = "Changed"),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot upload organization library file`()
    {
        val fixture = fixture()
        val entry = entry(BlueprintScope.ORG)
        whenever(fixture.repository.findById(entry.id)).thenReturn(entry)

        assertThrows<ForbiddenException> {
            fixture.service.uploadFile(entry.id, File("not-used.pdf"), "pdf")
        }
        verifyNoInteractions(fixture.storage)
    }

    @Test
    fun `APP_ADMIN without organization permission cannot download organization library file`()
    {
        val fixture = fixture()
        val entry = entry(BlueprintScope.ORG)
        whenever(fixture.repository.findById(entry.id)).thenReturn(entry)

        assertThrows<ForbiddenException> {
            fixture.service.downloadFile(entry.id)
        }
        verifyNoInteractions(fixture.storage, fixture.auditRecorder)
    }

    @Test
    fun `APP_ADMIN without organization permission cannot resolve organization library file`()
    {
        val fixture = fixture()
        val entry = entry(BlueprintScope.ORG)
        whenever(fixture.repository.findById(entry.id)).thenReturn(entry)

        assertThrows<ForbiddenException> {
            fixture.service.resolveLibraryFileForBlueprintDocument(entry.id)
        }
        verifyNoInteractions(fixture.storage)
    }

    @Test
    fun `APP_ADMIN without organization permission cannot publish organization library entry`()
    {
        val fixture = fixture()
        val entry = entry(BlueprintScope.ORG)
        whenever(fixture.repository.findById(entry.id)).thenReturn(entry)

        assertThrows<ForbiddenException> {
            fixture.service.patchPublished(
                entry.id,
                PatchDocumentLibraryPublishedRequest(isPublished = true),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot change organization library status`()
    {
        val fixture = fixture()
        val entry = entry(BlueprintScope.ORG)
        whenever(fixture.repository.findById(entry.id)).thenReturn(entry)

        assertThrows<ForbiddenException> {
            fixture.service.patchStatus(
                entry.id,
                PatchDocumentLibraryStatusRequest(isActive = false),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot delete organization library entry`()
    {
        val fixture = fixture()
        val entry = entry(BlueprintScope.ORG)
        whenever(fixture.repository.findById(entry.id)).thenReturn(entry)

        assertThrows<ForbiddenException> {
            fixture.service.deleteEntry(entry.id)
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot clone organization library entry`()
    {
        val fixture = fixture()
        val entry = entry(BlueprintScope.ORG)
        whenever(fixture.repository.findById(entry.id)).thenReturn(entry)

        assertThrows<ForbiddenException> {
            fixture.service.cloneEntry(entry.id, CloneDocumentLibraryEntryRequest())
        }
    }

    @Test
    fun `APP_ADMIN cannot clone into organization scope without organization role`()
    {
        val fixture = fixture()
        val entry = entry(BlueprintScope.APP)
        whenever(fixture.repository.findById(entry.id)).thenReturn(entry)

        assertThrows<ForbiddenException> {
            fixture.service.cloneEntry(
                entry.id,
                CloneDocumentLibraryEntryRequest(targetScope = BlueprintScope.ORG.name),
            )
        }
    }

    @Test
    fun `APP_ADMIN can update app library entry`()
    {
        val fixture = fixture()
        val entry = entry(BlueprintScope.APP)
        whenever(fixture.repository.findById(entry.id)).thenReturn(entry)
        whenever(fixture.repository.update(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.updateEntry(
            entry.id,
            UpdateDocumentLibraryEntryRequest(title = "Changed"),
        )

        assertEquals("Changed", result.title)
    }

    @Test
    fun `APP_ADMIN without organization role cannot create organization library entry`()
    {
        val fixture = fixture()

        assertThrows<ForbiddenException> {
            fixture.service.createEntry(
                CreateDocumentLibraryEntryRequest(
                    title = "Organization Document",
                    scope = BlueprintScope.ORG.name,
                )
            )
        }
    }

    @Test
    fun `app library entry never inherits active organization identifier`()
    {
        val fixture = fixture()
        whenever(fixture.repository.save(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.createEntry(
            CreateDocumentLibraryEntryRequest(
                title = "Platform Document",
                scope = BlueprintScope.APP.name,
            )
        )

        assertEquals(BlueprintScope.APP.name, result.scope)
        assertNull(result.organizationId)
        val saved = argumentCaptor<DocumentLibraryEntry>()
        verify(fixture.repository).save(saved.capture())
        assertNull(saved.firstValue.organizationId)
    }

    @Test
    fun `dual-role default creation uses active organization scope`()
    {
        val fixture = fixture(appAdmin = true, orgAdmin = true)
        whenever(fixture.repository.save(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.createEntry(
            CreateDocumentLibraryEntryRequest(title = "Organization Document")
        )

        assertEquals(BlueprintScope.ORG.name, result.scope)
        assertEquals(organizationId, result.organizationId)
    }
}
