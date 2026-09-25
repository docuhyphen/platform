package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestAmendment
import com.docuhyphen.app.api.model.entity.InformationRequestAmendmentChange
import com.docuhyphen.app.api.model.entity.InformationRequestAmendmentChangeKind
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeIntent
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAmendmentPlan
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAmendmentView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestAmendmentChangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestAmendmentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestResponseRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.util.UUID

@ApplicationScoped
class InformationRequestAmendmentRecorder @Inject constructor(
    private val amendmentRepository: InformationRequestAmendmentRepository,
    private val changeRepository: InformationRequestAmendmentChangeRepository,
    private val noticeRepository: InformationRequestNoticeIntentRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val responseRepository: InformationRequestResponseRepository,
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
        val notices = partyRepository.findActiveForRequest(request.id).map { party ->
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
            .filter { it.kind == InformationRequestAmendmentChangeKind.MEANING_CHANGED || it.kind == InformationRequestAmendmentChangeKind.ADDED }
            .map { it.templateRequirementId }
            .toSet()
        if (changedMeaning.isEmpty()) return
        val requirementIds = requirementRepository.findForRequest(request.id)
            .filter { it.sourceTemplateRequirementId in changedMeaning }
            .map { it.id }
            .toSet()
        responseRepository.findAllForRequest(request.id)
            .filter { it.informationRequestRequirementId in requirementIds }
            .filter { it.disposition != InformationRequestResponseDisposition.NOT_ANSWERED || it.narrative != null }
            .forEach { response ->
                response.reconfirmationRequiredByAmendmentId = amendment.id
                responseRepository.update(response)
            }
    }
}
