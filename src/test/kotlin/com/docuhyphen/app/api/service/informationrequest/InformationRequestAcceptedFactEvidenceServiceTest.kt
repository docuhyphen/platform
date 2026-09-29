package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionEvidence
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionItem
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionPackage
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionSupportingLink
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionPackageView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestAcceptedFactEvidenceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class InformationRequestAcceptedFactEvidenceServiceTest
{
    private val repository = mock<InformationRequestAcceptedFactEvidenceRepository>()
    private val service = InformationRequestAcceptedFactEvidenceService(repository)
    private val fieldRequirementId = UUID.randomUUID()
    private val supportingRequirementId = UUID.randomUUID()
    private val otherRequirementId = UUID.randomUUID()
    private val supportingItem = InformationRequestSubmissionItem().apply {
        informationRequestRequirementId = supportingRequirementId
    }
    private val unrelatedItem = InformationRequestSubmissionItem().apply {
        informationRequestRequirementId = otherRequirementId
    }
    private val supportingEvidence = InformationRequestSubmissionEvidence().apply {
        itemId = supportingItem.id
        evidenceVersionId = UUID.randomUUID()
        conformance = "CONFORMING"
    }
    private val unrelatedEvidence = InformationRequestSubmissionEvidence().apply {
        itemId = unrelatedItem.id
        evidenceVersionId = UUID.randomUUID()
        conformance = "CONFORMING"
    }
    private val link = InformationRequestSubmissionSupportingLink().apply {
        supportedRequirementId = fieldRequirementId
        supportingRequirementId = this@InformationRequestAcceptedFactEvidenceServiceTest.supportingRequirementId
    }
    private val packageView = InformationRequestSubmissionPackageView(
        submissionPackage = InformationRequestSubmissionPackage(),
        items = listOf(supportingItem, unrelatedItem),
        evidence = listOf(supportingEvidence, unrelatedEvidence),
        links = listOf(link),
        attestations = emptyList(),
        withdrawal = null,
    )

    @Test
    fun `only conforming submitted versions linked to the answer are promoted with exact provenance`()
    {
        val selected = service.select(packageView, fieldRequirementId, listOf(supportingEvidence.evidenceVersionId))
        assertEquals(listOf(supportingEvidence.id), selected.map { it.id })
        val factId = UUID.randomUUID()
        service.save(factId, selected)
        val references = argumentCaptor<com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactEvidence>()
        verify(repository).save(references.capture())
        assertEquals(factId, references.firstValue.factId)
        assertEquals(supportingEvidence.id, references.firstValue.sourceSubmissionEvidenceId)
        assertEquals(supportingEvidence.evidenceVersionId, references.firstValue.evidenceVersionId)

        assertThrows(InformationRequestCommandRequestException::class.java) {
            service.select(packageView, fieldRequirementId, listOf(unrelatedEvidence.evidenceVersionId))
        }
        assertThrows(InformationRequestCommandRequestException::class.java) {
            service.select(packageView, fieldRequirementId, listOf(supportingEvidence.evidenceVersionId, supportingEvidence.evidenceVersionId))
        }
        supportingEvidence.conformance = "QUARANTINED"
        assertThrows(InformationRequestCommandRequestException::class.java) {
            service.select(packageView, fieldRequirementId, listOf(supportingEvidence.evidenceVersionId))
        }
    }
}
