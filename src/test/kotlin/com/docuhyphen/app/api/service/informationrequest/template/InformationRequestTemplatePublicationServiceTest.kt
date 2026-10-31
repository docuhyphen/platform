package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateMutationContext
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.RevisionETag
import com.docuhyphen.app.api.service.informationrequest.template.model.TemplatePublicationFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class InformationRequestTemplatePublicationServiceTest
{
    private val actor = PrincipalRef.user(UUID.randomUUID())

    @Test
    fun `publication freezes the editable version through the one version freezer and records it`()
    {
        val fixture = fixture()

        val published = fixture.service.publishTemplate(fixture.definition.id, fixture.current(), "publish-key")

        assertSame(fixture.projected, published)
        inOrder(fixture.authoringService, fixture.versionRepository, fixture.freezer) {
            verify(fixture.authoringService).requireMutationContext(fixture.definition.id)
            verify(fixture.versionRepository).findDraftForUpdate(fixture.definition.id)
            verify(fixture.freezer).freeze(fixture.definition, fixture.version, actor)
            verify(fixture.authoringService).recordTemplateVersionEvent(
                AuditEventType.INFORMATION_REQUEST_TEMPLATE_PUBLISH,
                fixture.definition,
                fixture.version,
                actor,
                null,
            )
        }
    }

    @Test
    fun `publication requires an editable version`()
    {
        val fixture = fixture(hasDraft = false)

        assertThrows<IllegalStateException> {
            fixture.service.publishTemplate(fixture.definition.id, CommandPrecondition.Unconditioned, "publish-key")
        }

        verifyNoInteractions(fixture.freezer)
        verify(fixture.authoringService, never()).recordTemplateVersionEvent(any(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun `a refused freeze records no publication`()
    {
        val fixture = fixture()
        doThrow(InformationRequestTemplateValidationException("Information request template version 1 configures no requirements"))
            .whenever(fixture.freezer).freeze(any(), any(), any())

        assertThrows<InformationRequestTemplateValidationException> {
            fixture.service.publishTemplate(fixture.definition.id, fixture.current(), "publish-key")
        }

        verify(fixture.authoringService, never()).recordTemplateVersionEvent(any(), any(), any(), any(), anyOrNull())
        assertEquals(0, fixture.receipts.receipts.size)
    }

    @Test
    fun `a draft is published only under the tag it was read at`()
    {
        val fixture = fixture()

        val stale = assertThrows<CommandPreconditionException> {
            fixture.service.publishTemplate(
                fixture.definition.id,
                CommandPrecondition.ExpectedRevision(RevisionETag.of(fixture.version.id, 6)),
                "publish-key",
            )
        }
        val unstated = assertThrows<CommandPreconditionException> {
            fixture.service.publishTemplate(fixture.definition.id, CommandPrecondition.Absent, "other-key")
        }

        assertEquals(CommandPreconditionException.Kind.STALE, stale.kind)
        assertEquals(RevisionETag.of(fixture.version.id, 7), stale.currentETag)
        assertEquals(CommandPreconditionException.Kind.REQUIRED, unstated.kind)
        verifyNoInteractions(fixture.freezer)
    }

    @Test
    fun `a publication retried under its idempotency key answers again instead of failing on the frozen draft`()
    {
        val fixture = fixture()
        val tag = fixture.current()

        fixture.service.publishTemplate(fixture.definition.id, tag, "publish-once")
        whenever(fixture.versionRepository.findDraftForUpdate(fixture.definition.id)).thenReturn(null)
        val replayed = fixture.service.publishTemplate(fixture.definition.id, tag, "publish-once")

        assertSame(fixture.projected, replayed)
        verify(fixture.freezer, times(1)).freeze(any(), any(), any())
        verify(fixture.authoringService, times(1)).recordTemplateVersionEvent(any(), any(), any(), any(), anyOrNull())
        assertEquals(ResourceType.INFORMATION_REQUEST_TEMPLATE, fixture.receipts.receipts.single().resultResourceType)
        assertEquals(fixture.definition.id, fixture.receipts.receipts.single().resultResourceId)
    }

    private fun fixture(hasDraft: Boolean = true): TemplatePublicationFixture
    {
        val definition = InformationRequestTemplateDefinition().apply {
            scopeKind = InformationRequestTemplateScopeKind.PERSONAL
            scopeUserId = actor.id
            namespace = "process"
            templateKey = "collection-pattern"
            displayName = "Collection pattern"
        }
        val version = InformationRequestTemplateVersion().apply {
            templateDefinitionId = definition.id
            versionNumber = 1
            status = InformationRequestTemplateStatus.DRAFT
            revision = 7
        }
        val projected = mock<InformationRequestTemplateDto>()
        val authoringService = mock<InformationRequestTemplateAuthoringService>()
        whenever(authoringService.requireMutationContext(definition.id))
            .thenReturn(InformationRequestTemplateMutationContext(definition, actor))
        whenever(authoringService.projectTemplate(definition)).thenReturn(projected)
        val versionRepository = mock<InformationRequestTemplateVersionRepository>()
        whenever(versionRepository.findDraftForUpdate(definition.id)).thenReturn(version.takeIf { hasDraft })
        val freezer = mock<InformationRequestTemplateVersionFreezer>()
        val receipts = InMemoryTemplateReceiptStore()

        return TemplatePublicationFixture(
            service = InformationRequestTemplatePublicationService(
                authoringService,
                versionRepository,
                freezer,
                CommandReceiptService(receipts, CommandReceiptScope()),
            ),
            authoringService = authoringService,
            versionRepository = versionRepository,
            freezer = freezer,
            receipts = receipts,
            definition = definition,
            version = version,
            projected = projected,
        )
    }
}
