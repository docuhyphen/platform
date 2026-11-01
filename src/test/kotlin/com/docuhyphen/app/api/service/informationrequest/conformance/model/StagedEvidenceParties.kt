package com.docuhyphen.app.api.service.informationrequest.conformance.model

import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import java.util.*

internal data class StagedEvidenceParties(
    val subjectIdentityRefId: UUID,
    val reviewer: PrincipalRef,
    val reviewerPartyId: UUID,
    val delegate: PrincipalRef,
    val outsider: PrincipalRef,
)
