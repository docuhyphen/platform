package com.docuhyphen.app.api.model.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import java.util.*

data class InformationRequestCredentialCutoff(
    val actor: InformationRequestAuditActor,
    val reasonCode: String,
) {
    companion object {
        const val PARTY_REVOKED = "PARTY_REVOKED"
        const val PARTY_REASSIGNED = "PARTY_REASSIGNED"
        const val ORGANIZATION_MEMBER_REMOVED = "ORGANIZATION_MEMBER_REMOVED"
        const val GROUP_REMOVED = "GROUP_REMOVED"
        const val PARTICIPANT_ACCOUNT_LINKED = "PARTICIPANT_ACCOUNT_LINKED"
        const val PARENT_DELETED = "PARENT_DELETED"
        const val RECIPIENT_SIGN_IN_REQUIRED = "RECIPIENT_SIGN_IN_REQUIRED"
        const val REQUEST_CANCELLED = "REQUEST_CANCELLED"
        const val REQUEST_SUPERSEDED = "REQUEST_SUPERSEDED"
    }
}

data class RequestAccessSessionBinding(
    val session: RequestAccessSession,
    val partyId: UUID?,
)
