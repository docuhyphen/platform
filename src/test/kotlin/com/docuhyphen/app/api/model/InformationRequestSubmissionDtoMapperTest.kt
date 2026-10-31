package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceVersionRecord
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestReadableSubmissionPackage
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionPackageView
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class InformationRequestSubmissionDtoMapperTest
{
    private val requestId = UUID.randomUUID()
    private val principal = PrincipalRef.user(UUID.randomUUID())
    private val submissionPackage = InformationRequestSubmissionPackage().apply {
        informationRequestId = requestId
        packageNumber = 1
        templateVersionId = UUID.randomUUID()
        contentHashSha256 = "a".repeat(64)
        manifestHashSha256 = "b".repeat(64)
        submittedByPrincipalKind = principal.kind
        submittedByPrincipalId = principal.id
    }

    @Test
    fun `each visible item names its own submission item so a caller can act on that exact item`()
    {
        val visible = item("first_value")
        val hidden = item("second_value")
        val view = InformationRequestSubmissionPackageView(
            submissionPackage,
            listOf(visible, hidden),
            emptyList(),
            emptyList(),
            emptyList(),
            null
        )

        val dto = InformationRequestSubmissionDtoMapper.toDto(
            InformationRequestReadableSubmissionPackage(
                view,
                setOf(visible.informationRequestRequirementId),
                emptyMap(),
                emptyMap()
            ),
            principal,
        )

        assertEquals(listOf(visible.id), dto.items.map { it.id })
        assertEquals(listOf("first_value"), dto.items.map { it.requirementKey })
        assertEquals(1, dto.undisclosedItemCount)
    }

    @Test
    fun `each frozen file names the file name declared when it was collected`() {
        val item = item("recorded_file")
        val named = evidence(item, 2)
        val unnamed = evidence(item, 1)
        val view = InformationRequestSubmissionPackageView(
            submissionPackage,
            listOf(item),
            listOf(named, unnamed),
            emptyList(),
            emptyList(),
            null
        )
        val versions = mapOf(
            named.evidenceVersionId to InformationRequestEvidenceVersionRecord(
                evidenceVersionId = named.evidenceVersionId,
                evidenceArtifactId = named.evidenceArtifactId,
                versionNumber = 2,
                declaredFileName = "recorded-file.pdf",
                createdBy = principal,
            ),
        )

        val dto = InformationRequestSubmissionDtoMapper.toDto(
            InformationRequestReadableSubmissionPackage(
                view,
                setOf(item.informationRequestRequirementId),
                emptyMap(),
                versions
            ),
            principal,
        )

        val files = dto.items.single().evidence.associateBy { it.evidenceVersionId }
        assertEquals("recorded-file.pdf", files.getValue(named.evidenceVersionId).declaredFileName)
        assertEquals(2, files.getValue(named.evidenceVersionId).versionNumber)
        assertEquals(null, files.getValue(unnamed.evidenceVersionId).declaredFileName)
    }

    private fun item(key: String) = InformationRequestSubmissionItem().apply {
        packageId = submissionPackage.id
        informationRequestId = requestId
        informationRequestRequirementId = UUID.randomUUID()
        requirementRevisionId = UUID.randomUUID()
        templateBindingId = UUID.randomUUID()
        requirementKey = key
        requirementType = InformationRequestRequirementType.FIELD
        occurrencePath = ""
        completenessState = InformationRequestCompletenessItemState.COMPLETE
        disposition = InformationRequestResponseDisposition.PROVIDED
    }

    private fun evidence(item: InformationRequestSubmissionItem, number: Int) =
        InformationRequestSubmissionEvidence().apply {
            packageId = submissionPackage.id
            itemId = item.id
            informationRequestId = requestId
            evidenceArtifactId = UUID.randomUUID()
            evidenceVersionId = UUID.randomUUID()
            evidenceVersionNumber = number
            conformance = "CONFORMING"
        }
}
