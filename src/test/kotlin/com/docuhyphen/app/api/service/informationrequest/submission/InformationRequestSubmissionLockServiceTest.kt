package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestItemProblem
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementCorrectionScope
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestResponseEditLock
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestReturnedItem
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionWithdrawalRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestSubmissionLockServiceTest
{
    private val requestId = UUID.randomUUID()
    private val packageRepository: InformationRequestSubmissionPackageRepository = mock()
    private val itemRepository: InformationRequestSubmissionItemRepository = mock()
    private val withdrawalRepository: InformationRequestSubmissionWithdrawalRepository = mock()
    private val correctionRepository: InformationRequestCorrectionRepository = mock()
    private val correctionItemRepository: InformationRequestCorrectionItemRepository = mock()
    private val service = InformationRequestSubmissionLockService(
        packageRepository,
        itemRepository,
        mock<InformationRequestSubmissionEvidenceRepository>(),
        withdrawalRepository,
        correctionRepository,
        correctionItemRepository,
        mock<InformationRequestCorrectionEvidenceRepository>(),
        mock<InformationRequestSubmissionStages>(),
        mock(),
        mock(),
        mock(),
        mock(),
    )

    private val submitted = InformationRequestSubmissionPackage().apply { informationRequestId = requestId }
    private val returned =
        item(InformationRequestRequirementType.FIELD, InformationRequestCompletenessItemState.COMPLETE)
    private val hidden = item(InformationRequestRequirementType.FIELD, InformationRequestCompletenessItemState.HIDDEN)
    private val kept =
        item(InformationRequestRequirementType.DOCUMENT, InformationRequestCompletenessItemState.COMPLETE)
    private val assertion =
        item(InformationRequestRequirementType.RESPONSE_ATTESTATION, InformationRequestCompletenessItemState.COMPLETE)

    init
    {
        whenever(packageRepository.findForRequest(requestId)).thenReturn(listOf(submitted))
        whenever(withdrawalRepository.findForRequest(requestId)).thenReturn(emptyList())
        whenever(itemRepository.findForPackages(any())).thenReturn(listOf(returned, hidden, kept, assertion))
    }

    @Test
    fun `under an open correction only returned items and items a condition hid at submission reopen, yet all stay submitted`()
    {
        val correction = InformationRequestCorrection().apply {
            informationRequestId = requestId
            packageId = submitted.id
        }
        whenever(correctionRepository.findOpenForRequest(requestId)).thenReturn(listOf(correction))
        whenever(correctionItemRepository.findForCorrections(listOf(correction.id))).thenReturn(
            listOf(
                InformationRequestCorrectionItem().apply {
                    correctionId = correction.id
                    informationRequestId = requestId
                    submissionItemId = returned.id
                    requirementId = returned.informationRequestRequirementId
                },
            ),
        )

        assertEquals(
            listOf(returned, hidden, kept, assertion).map { it.informationRequestRequirementId }.toSet(),
            service.submittedRequirementIds(requestId),
        )
        assertEquals(InformationRequestRequirementCorrectionScope.CORRECTION_ALLOWED, scopeOf(returned))
        assertEquals(InformationRequestRequirementCorrectionScope.CORRECTION_ALLOWED, scopeOf(hidden))
        assertEquals(InformationRequestRequirementCorrectionScope.CORRECTION_EXCLUDED, scopeOf(kept))
        assertEquals(InformationRequestRequirementCorrectionScope.CORRECTION_ALLOWED, scopeOf(assertion))
        assertDoesNotThrow {
            service.requireUnlocked(
                requestId,
                listOf(returned.informationRequestRequirementId, hidden.informationRequestRequirementId)
            )
        }
        assertDoesNotThrow { service.requireAttestationOpen(requestId, assertion.informationRequestRequirementId) }
        listOf(kept, assertion).forEach { locked ->
            val refusal = assertThrows<InformationRequestLifecycleException> {
                service.requireUnlocked(requestId, listOf(locked.informationRequestRequirementId))
            }
            assertEquals(InformationRequestErrorCatalog.CORRECTION_SCOPE_DENIED, refusal.reasonCode)
        }
    }

    @Test
    fun `without a correction every submitted item stays locked, including one a condition hid`()
    {
        whenever(correctionRepository.findOpenForRequest(requestId)).thenReturn(emptyList())
        whenever(correctionItemRepository.findForCorrections(any())).thenReturn(emptyList())

        assertEquals(
            listOf(returned, hidden, kept, assertion).map { it.informationRequestRequirementId }.toSet(),
            service.submittedRequirementIds(requestId),
        )
        assertEquals(InformationRequestRequirementCorrectionScope.NORMAL_RESPONSE, scopeOf(hidden))
        val refusal = assertThrows<InformationRequestLifecycleException> {
            service.requireUnlocked(requestId, listOf(hidden.informationRequestRequirementId))
        }
        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
    }

    @Test
    fun `under an open correction an answer that was not returned is locked for that reason and every other one is open`() {
        val correction = InformationRequestCorrection().apply {
            informationRequestId = requestId
            packageId = submitted.id
        }
        whenever(correctionRepository.findOpenForRequest(requestId)).thenReturn(listOf(correction))
        whenever(correctionItemRepository.findForCorrections(listOf(correction.id))).thenReturn(
            listOf(
                InformationRequestCorrectionItem().apply {
                    correctionId = correction.id
                    informationRequestId = requestId
                    submissionItemId = returned.id
                    requirementId = returned.informationRequestRequirementId
                },
            ),
        )
        val unsubmitted = UUID.randomUUID()

        val locks = service.editLocks(
            requestId,
            listOf(returned, hidden, kept).map { it.informationRequestRequirementId } + unsubmitted,
        )

        assertEquals(
            mapOf(kept.informationRequestRequirementId to InformationRequestResponseEditLock.NOT_RETURNED_FOR_CORRECTION),
            locks,
        )
    }

    @Test
    fun `without a correction every submitted answer is locked as submitted`() {
        whenever(correctionRepository.findOpenForRequest(requestId)).thenReturn(emptyList())
        whenever(correctionItemRepository.findForCorrections(any())).thenReturn(emptyList())

        val locks = service.editLocks(requestId, listOf(returned, kept).map { it.informationRequestRequirementId })

        assertEquals(
            mapOf(
                returned.informationRequestRequirementId to InformationRequestResponseEditLock.SUBMITTED,
                kept.informationRequestRequirementId to InformationRequestResponseEditLock.SUBMITTED,
            ),
            locks,
        )
    }

    @Test
    fun `one refusal names every locked Requirement a change addresses`() {
        whenever(correctionRepository.findOpenForRequest(requestId)).thenReturn(emptyList())
        whenever(correctionItemRepository.findForCorrections(any())).thenReturn(emptyList())

        val refusal = assertThrows<InformationRequestItemRefusalException> {
            service.requireUnlocked(
                requestId,
                listOf(returned.informationRequestRequirementId, kept.informationRequestRequirementId)
            )
        }

        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
        assertEquals(
            listOf(
                InformationRequestItemProblem(
                    code = InformationRequestErrorCatalog.SUBMISSION_LOCKED,
                    message = refusal.problems.first().message,
                    requirementId = returned.informationRequestRequirementId,
                    occurrencePath = returned.occurrencePath,
                ),
                InformationRequestItemProblem(
                    code = InformationRequestErrorCatalog.SUBMISSION_LOCKED,
                    message = refusal.problems.last().message,
                    requirementId = kept.informationRequestRequirementId,
                    occurrencePath = kept.occurrencePath,
                ),
            ),
            refusal.problems,
        )
        assertFalse(refusal.message.contains(returned.informationRequestRequirementId.toString()))
    }

    @Test
    fun `an open correction lists each returned Requirement with the response revision it was submitted with`() {
        val correction = InformationRequestCorrection().apply {
            informationRequestId = requestId
            packageId = submitted.id
        }
        returned.responseRevision = 3
        whenever(correctionRepository.findOpenForRequest(requestId)).thenReturn(listOf(correction))
        whenever(correctionItemRepository.findForCorrections(listOf(correction.id))).thenReturn(
            listOf(returned, kept).map { item ->
                InformationRequestCorrectionItem().apply {
                    correctionId = correction.id
                    informationRequestId = requestId
                    submissionItemId = item.id
                    requirementId = item.informationRequestRequirementId
                }
            },
        )

        assertEquals(
            setOf(
                InformationRequestReturnedItem(
                    returned.informationRequestRequirementId,
                    InformationRequestRequirementType.FIELD,
                    3
                ),
                InformationRequestReturnedItem(
                    kept.informationRequestRequirementId,
                    InformationRequestRequirementType.DOCUMENT,
                    null
                ),
            ),
            service.returnedItems(requestId).toSet(),
        )
    }

    @Test
    fun `without an open correction nothing is listed as returned`() {
        whenever(correctionRepository.findOpenForRequest(requestId)).thenReturn(emptyList())

        assertEquals(emptyList<InformationRequestReturnedItem>(), service.returnedItems(requestId))
    }

    private fun scopeOf(item: InformationRequestSubmissionItem) =
        service.correctionScopeOf(requestId, item.informationRequestRequirementId)

    private fun item(type: InformationRequestRequirementType, state: InformationRequestCompletenessItemState) =
        InformationRequestSubmissionItem().apply {
            packageId = submitted.id
            informationRequestId = requestId
            informationRequestRequirementId = UUID.randomUUID()
            requirementType = type
            completenessState = state
            occurrencePath = "root"
        }
}
