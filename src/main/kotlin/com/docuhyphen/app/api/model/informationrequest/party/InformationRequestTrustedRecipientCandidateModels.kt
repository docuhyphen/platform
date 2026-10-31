package com.docuhyphen.app.api.model.informationrequest.party

import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import java.util.*

data class InformationRequestTrustedRecipientCandidate(
    val exchangeRecipientId: UUID,
    val selectionType: ExchangeRecipientSelectionType,
    val principal: PrincipalRef,
    val label: String,
    val targetOrganizationId: UUID?,
    val targetOrganizationName: String?,
    val trustSuspended: Boolean,
)
