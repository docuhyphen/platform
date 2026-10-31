package com.docuhyphen.app.api.model.informationrequest.template

import com.docuhyphen.app.api.model.entity.*
import java.sql.Timestamp
import java.util.*

data class InformationRequestMaterializationResult(
    val requirementCount: Int,
)

data class InformationRequestTemplateVersionContent(
    val templateKey: String,
    val versionNumber: Int,
    val contentHashSha256: String,
)

data class InformationRequestTemplateVersionDescription(
    val templateVersionId: UUID,
    val templateDefinitionId: UUID,
    val templateDisplayName: String,
    val versionNumber: Int,
    val status: InformationRequestTemplateStatus,
    val latest: Boolean,
    val latestPublishedVersionNumber: Int?,
    val retiredAt: Timestamp?,
)

internal data class InformationRequestTemplateBindingConfiguration(
    val dispositionsByBinding: Map<UUID, List<InformationRequestTemplateBindingDisposition>>,
    val policiesByBinding: Map<UUID, InformationRequestTemplateEvidencePolicy>,
    val acceptedValuesByPolicy: Map<UUID, List<InformationRequestTemplateEvidenceAcceptedValue>>,
    val substitutesByBinding: Map<UUID, List<InformationRequestTemplateBindingSubstitute>>,
    val evidenceLinksByBinding: Map<UUID, List<InformationRequestTemplateBindingEvidenceLink>>,
)

internal data class InformationRequestTemplateProjectionBindingConfiguration(
    val dispositionsByBinding: Map<UUID, List<InformationRequestTemplateBindingDisposition>>,
    val policiesByBinding: Map<UUID, InformationRequestTemplateEvidencePolicy>,
    val acceptedValuesByPolicy: Map<UUID, List<InformationRequestTemplateEvidenceAcceptedValue>>,
    val substitutesByBinding: Map<UUID, List<InformationRequestTemplateBindingSubstitute>>,
    val evidenceLinksByBinding: Map<UUID, List<InformationRequestTemplateBindingEvidenceLink>>,
    val attestationPoliciesByBinding: Map<UUID, InformationRequestTemplateAttestationPolicy>,
    val attestationRolesByPolicy: Map<UUID, List<InformationRequestTemplateAttestationRole>>,
)

data class InformationRequestTemplateCollectedField(
    val sectionKey: String,
    val requirementKey: String,
    val fieldDefinitionId: UUID,
)
