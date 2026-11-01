package com.docuhyphen.app.api.service.informationrequest.amendment

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.amendment.InformationRequestAmendmentPlan
import com.docuhyphen.app.api.model.informationrequest.amendment.InformationRequestAmendmentRequirementChange
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestAmendmentChangeRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestAmendmentRepository
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestAmendmentReconfirmationTest {
    private val amendments = mock<InformationRequestAmendmentRepository>()
    private val changes = mock<InformationRequestAmendmentChangeRepository>()
    private val requirements = mock<InformationRequestRequirementRepository>()
    private val responses = mock<InformationRequestResponseRepository>()
    private val recorder = InformationRequestAmendmentRecorder(
        amendments,
        changes,
        mock(),
        mock(),
        requirements,
        responses,
        mock(),
        mock()
    )

    @Test
    fun `a value-only answer requires reconfirmation when its meaning changes`() {
        val response = record(InformationRequestAmendmentChangeKind.MEANING_CHANGED, fieldValue = true)
        assertNotNull(response.reconfirmationRequiredByAmendmentId)
        verify(responses).update(response)
    }

    @Test
    fun `a newly added requirement never reconfirms an earlier response`() {
        val response = record(InformationRequestAmendmentChangeKind.ADDED, fieldValue = false)
        assertNull(response.reconfirmationRequiredByAmendmentId)
        verify(responses, never()).update(any())
    }

    private fun record(kind: InformationRequestAmendmentChangeKind, fieldValue: Boolean): InformationRequestResponse {
        val request = InformationRequest().apply {
            templateVersionId = UUID.randomUUID(); state =
            com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState.DRAFT
        }
        val requirement = InformationRequestRequirement().apply { sourceTemplateRequirementId = UUID.randomUUID() }
        val response = InformationRequestResponse().apply {
            informationRequestRequirementId = requirement.id
            if (fieldValue) fieldValueSetId = UUID.randomUUID() else disposition =
                InformationRequestResponseDisposition.PROVIDED
        }
        whenever(amendments.save(any())).thenAnswer { it.arguments[0] }
        whenever(changes.save(any())).thenAnswer { it.arguments[0] }
        whenever(requirements.findForRequest(request.id)).thenReturn(listOf(requirement))
        whenever(responses.findAllForRequest(request.id)).thenReturn(listOf(response))
        val plan = InformationRequestAmendmentPlan(
            listOf(
                InformationRequestAmendmentRequirementChange(
                    "record",
                    requirement.sourceTemplateRequirementId,
                    kind,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    null,
                    false
                )
            ),
            emptyList(), false, false,
        )
        recorder.record(
            request,
            UUID.randomUUID(),
            plan,
            null,
            PrincipalRef.user(UUID.randomUUID()),
            Timestamp.from(Instant.now())
        )
        return response
    }
}