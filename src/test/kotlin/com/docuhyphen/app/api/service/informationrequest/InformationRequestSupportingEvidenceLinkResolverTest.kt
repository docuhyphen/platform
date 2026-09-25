package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingEvidenceLink
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSupportingEvidenceLinkTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class InformationRequestSupportingEvidenceLinkResolverTest
{
    private val supportedBinding = UUID.randomUUID()
    private val documentBinding = UUID.randomUUID()
    private val templateLink = InformationRequestTemplateBindingEvidenceLink().apply {
        templateBindingId = supportedBinding
        supportingTemplateBindingId = documentBinding
        templateVersionId = UUID.randomUUID()
    }

    @Test
    fun `a supported occurrence is linked to the document requested at its own occurrence`()
    {
        val supported = requirement(supportedBinding, "reported-item[1]")
        val own = requirement(documentBinding, "reported-item[1]")
        val other = requirement(documentBinding, "reported-item[0]")

        assertEquals(listOf(link(supported, own)), resolve(supported, own, other))
    }

    @Test
    fun `without a document at its own occurrence it is linked to the nearest enclosing one`()
    {
        val nested = requirement(supportedBinding, "reported-item[1]/detail[0]")
        val enclosing = requirement(documentBinding, "reported-item[1]")
        val root = requirement(documentBinding, "root")

        assertEquals(listOf(link(nested, enclosing)), resolve(nested, enclosing, root))

        val onlyRoot = requirement(supportedBinding, "reported-item[2]")
        assertEquals(listOf(link(onlyRoot, root)), resolve(onlyRoot, root))
    }

    @Test
    fun `a root answer is linked to every occurrence of a document requested only inside a group`()
    {
        val supported = requirement(supportedBinding, "root")
        val first = requirement(documentBinding, "reported-item[0]")
        val second = requirement(documentBinding, "reported-item[1]/detail[0]")

        assertEquals(listOf(link(supported, first), link(supported, second)), resolve(supported, first, second))
    }

    @Test
    fun `an answer inside one group occurrence is never linked to documents of another`()
    {
        val supported = requirement(supportedBinding, "reported-item[1]")
        val elsewhere = requirement(documentBinding, "reported-item[0]")
        val sibling = requirement(documentBinding, "other-item[0]")

        assertEquals(emptyList<InformationRequestSupportingEvidenceLinkTarget>(), resolve(supported, elsewhere, sibling))
    }

    @Test
    fun `a nested answer is linked to documents of its own descendants when none encloses it`()
    {
        val supported = requirement(supportedBinding, "reported-item[1]")
        val descendant = requirement(documentBinding, "reported-item[1]/detail[3]")
        val unrelated = requirement(documentBinding, "reported-item[10]/detail[0]")

        assertEquals(listOf(link(supported, descendant)), resolve(supported, descendant, unrelated))
    }

    private fun resolve(vararg requirements: InformationRequestRequirement) =
        InformationRequestSupportingEvidenceLinkResolver.resolve(listOf(templateLink), requirements.toList())

    private fun link(supported: InformationRequestRequirement, supporting: InformationRequestRequirement) =
        InformationRequestSupportingEvidenceLinkTarget(templateLink.id, supported.id, supporting.id)

    private fun requirement(bindingId: UUID, path: String) = InformationRequestRequirement().apply {
        informationRequestId = UUID.randomUUID()
        sourceTemplateVersionId = templateLink.templateVersionId
        sourceTemplateRequirementId = UUID.randomUUID()
        sourceTemplateBindingId = bindingId
        occurrencePath = path
    }
}
