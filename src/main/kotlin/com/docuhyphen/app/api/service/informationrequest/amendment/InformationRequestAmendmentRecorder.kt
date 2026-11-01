package com.docuhyphen.app.api.service.informationrequest.amendment

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.amendment.InformationRequestAmendmentPlan
import com.docuhyphen.app.api.model.informationrequest.amendment.InformationRequestAmendmentView
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestAmendmentChangeRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestAmendmentRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestHiddenResponseFiles
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class InformationRequestAmendmentRecorder @Inject constructor(
    private val amendmentRepository: InformationRequestAmendmentRepository,
    private val changeRepository: InformationRequestAmendmentChangeRepository,
    private val noticeRepository: InformationRequestNoticeIntentRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val responseRepository: InformationRequestResponseRepository,
    private val revisionRepository: InformationRequestRequirementRevisionRepository,
    private val files: InformationRequestHiddenResponseFiles,
)
{
    @Suppress("LongParameterList")
    fun record(
        request: InformationRequest,
        fromTemplateVersionId: UUID,
        plan: InformationRequestAmendmentPlan,
        reasonCode: String?,
        actor: PrincipalRef,
        now: Timestamp,
    ): InformationRequestAmendmentView
    {
        val amendment = amendmentRepository.save(
            InformationRequestAmendment().apply {
                informationRequestId = request.id
                amendmentNumber = amendmentRepository.findForRequest(request.id).size + 1
                this.fromTemplateVersionId = fromTemplateVersionId
                toTemplateVersionId = request.templateVersionId
                this.reasonCode = reasonCode?.trim()?.ifBlank { null }
                amendedByPrincipalKind = actor.kind
                amendedByPrincipalId = actor.id
                amendedAt = now
            },
        )
        val changes = plan.changes.map { change ->
            changeRepository.save(
                InformationRequestAmendmentChange().apply {
                    amendmentId = amendment.id
                    informationRequestId = request.id
                    templateRequirementId = change.templateRequirementId
                    requirementKey = change.requirementKey
                    changeKind = change.kind
                    fromTemplateBindingId = change.fromBindingId
                    toTemplateBindingId = change.toBindingId
                    reconfirmationRequired = change.kind == InformationRequestAmendmentChangeKind.MEANING_CHANGED
                },
            )
        }
        requireReconfirmation(request, plan, amendment)
        val notified =
            if (request.state == InformationRequestState.DRAFT) emptyList() else partyRepository.findActiveForRequest(
                request.id
            )
        val notices = notified.map { party ->
            noticeRepository.save(
                InformationRequestNoticeIntent().apply {
                    informationRequestId = request.id
                    amendmentId = amendment.id
                    partyId = party.id
                    createdAt = now
                },
            )
        }
        return InformationRequestAmendmentView(amendment, changes, notices)
    }

    private fun requireReconfirmation(
        request: InformationRequest,
        plan: InformationRequestAmendmentPlan,
        amendment: InformationRequestAmendment,
    )
    {
        val changedMeaning = plan.changes
            .filter { it.kind == InformationRequestAmendmentChangeKind.MEANING_CHANGED }
            .map { it.templateRequirementId }
            .toSet()
        if (changedMeaning.isEmpty()) return
        val existing =
            responseRepository.findAllForRequest(request.id).associateBy { it.informationRequestRequirementId }
        requirementRepository.findForRequest(request.id)
            .filter { it.sourceTemplateRequirementId in changedMeaning }
            .forEach { requirement ->
                val previous = existing[requirement.id]
                val hasAnswer = previous?.let {
                    it.disposition != InformationRequestResponseDisposition.NOT_ANSWERED ||
                            !it.narrative.isNullOrBlank() || it.fieldValueSetId != null
                } == true || files.active(requirement.id).isNotEmpty()
                if (!hasAnswer) return@forEach
                val response = previous ?: InformationRequestResponse().apply {
                    informationRequestId = request.id
                    informationRequestRequirementId = requirement.id
                    requirementRevisionId =
                        requireNotNull(revisionRepository.findCurrentForRequirement(requirement.id)).id
                    occurrencePath = requirement.occurrencePath
                    responseRevision = request.responseRevision
                    recordedByPrincipalKind = amendment.amendedByPrincipalKind
                    recordedByPrincipalId = amendment.amendedByPrincipalId
                    createdAt = amendment.amendedAt
                    updatedAt = amendment.amendedAt
                }
                response.reconfirmationRequiredByAmendmentId = amendment.id
                if (previous == null) responseRepository.save(response) else responseRepository.update(response)
            }
    }
}