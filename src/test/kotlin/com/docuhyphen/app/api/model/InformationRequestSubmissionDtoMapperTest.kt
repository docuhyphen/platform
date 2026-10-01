package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionItem
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionPackage
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestReadableSubmissionPackage
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionPackageView
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

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
        val view = InformationRequestSubmissionPackageView(submissionPackage, listOf(visible, hidden), emptyList(), emptyList(), emptyList(), null)

        val dto = InformationRequestSubmissionDtoMapper.toDto(
            InformationRequestReadableSubmissionPackage(view, setOf(visible.informationRequestRequirementId), emptyMap()),
            principal,
        )

        assertEquals(listOf(visible.id), dto.items.map { it.id })
        assertEquals(listOf("first_value"), dto.items.map { it.requirementKey })
        assertEquals(1, dto.undisclosedItemCount)
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
}
