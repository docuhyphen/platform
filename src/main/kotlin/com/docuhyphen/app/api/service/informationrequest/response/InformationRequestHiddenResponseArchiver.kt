package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.dto.FieldValueDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestArchivedEvidence
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseArchiveEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseArchiveRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp

@ApplicationScoped
class InformationRequestHiddenResponseArchiver @Inject constructor(
    private val archiveRepository: InformationRequestResponseArchiveRepository,
    private val archiveEvidenceRepository: InformationRequestResponseArchiveEvidenceRepository,
) {
    @Suppress("LongParameterList")
    fun archive(
        requirement: InformationRequestRequirement,
        response: InformationRequestResponse,
        ruleKey: String,
        collectedField: FieldValueDto?,
        fieldValueSetETag: String?,
        evidence: List<InformationRequestArchivedEvidence>,
        access: RequestAccessContext,
        now: Timestamp,
    ): InformationRequestResponseArchive? {
        val field = collectedField?.takeUnless { it.isEmpty }
        if (response.disposition == InformationRequestResponseDisposition.NOT_ANSWERED &&
            response.narrative == null &&
            field == null &&
            evidence.isEmpty()
        ) {
            return null
        }
        val archive = archiveRepository.save(
            InformationRequestResponseArchive().apply {
                informationRequestId = response.informationRequestId
                informationRequestRequirementId = requirement.id
                responseId = response.id
                occurrencePath = requirement.occurrencePath
                conditionRuleKey = ruleKey
                responseRevision = response.responseRevision
                disposition = response.disposition
                narrative = response.narrative
                fieldContractId = field?.fieldContractId
                fieldValueType = field?.valueType
                fieldValueJson = field?.value?.toString()
                this.fieldValueSetETag = if (field != null) fieldValueSetETag else null
                evidenceCount = evidence.size
                archivedByPrincipalKind = access.principal.kind
                archivedByPrincipalId = access.principal.id
                archivedBySessionRef = access.authorization.sessionRef
                archivedAt = now
            },
        )
        evidence.forEach { member ->
            archiveEvidenceRepository.save(
                InformationRequestResponseArchiveEvidence().apply {
                    archiveId = archive.id
                    informationRequestId = archive.informationRequestId
                    evidenceArtifactId = member.artifactId
                    evidenceVersionId = member.versionId
                    evidenceVersionNumber = member.versionNumber
                },
            )
        }
        return archive
    }
}
