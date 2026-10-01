package com.docuhyphen.app.api.model.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateAttestationPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateAttestationRole
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingEvidenceLink
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingSubstitute
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidenceAcceptedValue
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidencePolicy
import java.util.UUID

data class InformationRequestMaterializationResult(
    val requirementCount: Int,
)

internal data class InformationRequestTemplateBindingConfiguration(
    val dispositionsByBinding: Map<UUID, List<InformationRequestTemplateBindingDisposition>>,
    val policiesByBinding: Map<UUID, InformationRequestTemplateEvidencePolicy>,
    val acceptedValuesByPolicy: Map<UUID, List<InformationRequestTemplateEvidenceAcceptedValue>>,
    val substitutesByBinding: Map<UUID, List<InformationRequestTemplateBindingSubstitute>>,
    val evidenceLinksByBinding: Map<UUID, List<InformationRequestTemplateBindingEvidenceLink>>,
)

/** Everything one version states about its requirements, indexed by the row it belongs to. */
internal data class InformationRequestTemplateProjectionBindingConfiguration(
    val dispositionsByBinding: Map<UUID, List<InformationRequestTemplateBindingDisposition>>,
    val policiesByBinding: Map<UUID, InformationRequestTemplateEvidencePolicy>,
    val acceptedValuesByPolicy: Map<UUID, List<InformationRequestTemplateEvidenceAcceptedValue>>,
    val substitutesByBinding: Map<UUID, List<InformationRequestTemplateBindingSubstitute>>,
    val evidenceLinksByBinding: Map<UUID, List<InformationRequestTemplateBindingEvidenceLink>>,
    val attestationPoliciesByBinding: Map<UUID, InformationRequestTemplateAttestationPolicy>,
    val attestationRolesByPolicy: Map<UUID, List<InformationRequestTemplateAttestationRole>>,
)
