package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestSupportingEvidenceLink
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingEvidenceLink
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSupportingEvidenceLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingEvidenceLinkRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestSupportingEvidenceLinkServiceTest
{
    private val templateLinkRepository: InformationRequestTemplateBindingEvidenceLinkRepository = mock()
    private val requirementRepository: InformationRequestRequirementRepository = mock()
    private val linkRepository: InformationRequestSupportingEvidenceLinkRepository = mock()
    private val service = InformationRequestSupportingEvidenceLinkService(templateLinkRepository, requirementRepository, linkRepository)

    private val request = InformationRequest().apply {
        id = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
    }
    private val templateLink = InformationRequestTemplateBindingEvidenceLink().apply {
        templateBindingId = UUID.randomUUID()
        supportingTemplateBindingId = UUID.randomUUID()
        templateVersionId = request.templateVersionId
    }
    private val stored = mutableListOf<InformationRequestSupportingEvidenceLink>()

    init
    {
        whenever(linkRepository.findForRequest(request.id)).thenAnswer { stored.toList() }
        whenever(linkRepository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as InformationRequestSupportingEvidenceLink).also { stored += it }
        }
    }

    @Test
    fun `each resolved link is materialized once, however often materialization runs`()
    {
        val supported = requirement(templateLink.templateBindingId, "root")
        val first = requirement(templateLink.supportingTemplateBindingId, "reported-item[0]")
        whenever(templateLinkRepository.findForVersion(request.templateVersionId)).thenReturn(listOf(templateLink))
        whenever(requirementRepository.findForRequest(request.id)).thenReturn(listOf(supported, first))

        service.materialize(request)
        service.materialize(request)

        assertEquals(1, stored.size)
        val link = stored.single()
        assertEquals(request.id, link.informationRequestId)
        assertEquals(supported.id, link.supportedRequirementId)
        assertEquals(first.id, link.supportingRequirementId)
        assertEquals(templateLink.id, link.templateEvidenceLinkId)

        val second = requirement(templateLink.supportingTemplateBindingId, "reported-item[1]")
        whenever(requirementRepository.findForRequest(request.id)).thenReturn(listOf(supported, first, second))
        service.materialize(request)

        assertEquals(listOf(first.id, second.id), stored.map { it.supportingRequirementId })
    }

    @Test
    fun `a Template Version without evidence links materializes nothing`()
    {
        whenever(templateLinkRepository.findForVersion(request.templateVersionId)).thenReturn(emptyList())

        service.materialize(request)

        verify(requirementRepository, never()).findForRequest(any())
        verify(linkRepository, never()).save(any())
    }

    @Test
    fun `only links whose both ends the caller may see are projected`()
    {
        val visibleSupported = requirement(templateLink.templateBindingId, "root")
        val visibleDocument = requirement(templateLink.supportingTemplateBindingId, "reported-item[0]")
        val hiddenDocument = requirement(templateLink.supportingTemplateBindingId, "reported-item[1]")
        whenever(templateLinkRepository.findForVersion(request.templateVersionId)).thenReturn(listOf(templateLink))
        whenever(requirementRepository.findForRequest(request.id)).thenReturn(listOf(visibleSupported, visibleDocument, hiddenDocument))
        stored += link(visibleSupported.id, visibleDocument.id)
        stored += link(visibleSupported.id, hiddenDocument.id)

        val projected = service.visibleLinks(request, setOf(visibleSupported.id, visibleDocument.id))

        assertEquals(listOf(visibleDocument.id), projected.map { it.supportingRequirementId })
    }

    @Test
    fun `a stored link the pinned Version no longer names is not current`()
    {
        val supported = requirement(templateLink.templateBindingId, "root")
        val document = requirement(templateLink.supportingTemplateBindingId, "root")
        whenever(templateLinkRepository.findForVersion(request.templateVersionId)).thenReturn(emptyList())
        whenever(requirementRepository.findForRequest(request.id)).thenReturn(listOf(supported, document))
        stored += link(supported.id, document.id)

        assertEquals(emptyList<UUID>(), service.linksFrom(request, setOf(supported.id)).map { it.id })
    }

    private fun link(supported: UUID, supporting: UUID) = InformationRequestSupportingEvidenceLink().apply {
        informationRequestId = request.id
        supportedRequirementId = supported
        supportingRequirementId = supporting
        templateEvidenceLinkId = templateLink.id
    }

    private fun requirement(bindingId: UUID, path: String) = InformationRequestRequirement().apply {
        informationRequestId = request.id
        sourceTemplateVersionId = request.templateVersionId
        sourceTemplateRequirementId = UUID.randomUUID()
        sourceTemplateBindingId = bindingId
        occurrencePath = path
    }
}
