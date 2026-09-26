package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestCorrection
import com.docuhyphen.app.api.model.entity.InformationRequestCorrectionItem
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionItem
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionPackage
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCorrectionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCorrectionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionWithdrawalRepository
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessItemState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

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
    )

    private val submitted = InformationRequestSubmissionPackage().apply { informationRequestId = requestId }
    private val returned = item(InformationRequestRequirementType.FIELD, InformationRequestCompletenessItemState.COMPLETE)
    private val hidden = item(InformationRequestRequirementType.FIELD, InformationRequestCompletenessItemState.HIDDEN)
    private val kept = item(InformationRequestRequirementType.DOCUMENT, InformationRequestCompletenessItemState.COMPLETE)
    private val assertion = item(InformationRequestRequirementType.RESPONSE_ATTESTATION, InformationRequestCompletenessItemState.COMPLETE)

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
            service.requireUnlocked(requestId, listOf(returned.informationRequestRequirementId, hidden.informationRequestRequirementId))
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

    private fun scopeOf(item: InformationRequestSubmissionItem) =
        service.correctionScopeOf(requestId, item.informationRequestRequirementId)

    private fun item(type: InformationRequestRequirementType, state: InformationRequestCompletenessItemState) =
        InformationRequestSubmissionItem().apply {
            packageId = submitted.id
            informationRequestId = requestId
            informationRequestRequirementId = UUID.randomUUID()
            requirementType = type
            completenessState = state
        }
}
