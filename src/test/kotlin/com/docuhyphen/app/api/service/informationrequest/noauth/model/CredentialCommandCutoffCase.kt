package com.docuhyphen.app.api.service.informationrequest.noauth.model

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog

data class CredentialCommandCutoffCase(
    val label: String,
    val requestState: InformationRequestState,
    val parentStatus: ExchangeStatus,
    val parentDeleted: Boolean,
    val reasonCode: String,
) {
    val parentSnapshot: InformationRequestParentSnapshot
        get() = InformationRequestParentSnapshot(parentStatus, parentDeleted, lockedForUpdate = true)

    fun applyTo(request: InformationRequest, parent: Exchange) {
        request.state = requestState
        parent.status = parentStatus
        parent.isDeleted = parentDeleted
    }

    override fun toString(): String = label

    companion object {
        const val SOURCE =
            "com.docuhyphen.app.api.service.informationrequest.noauth.model.CredentialCommandCutoffCase#cases"

        @JvmStatic
        fun cases(): List<CredentialCommandCutoffCase> = listOf(
            CredentialCommandCutoffCase(
                "cancelled request",
                InformationRequestState.CANCELLED,
                ExchangeStatus.ACCEPTED_STARTED,
                false,
                InformationRequestErrorCatalog.STATE_INVALID,
            ),
            CredentialCommandCutoffCase(
                "superseded request",
                InformationRequestState.SUPERSEDED,
                ExchangeStatus.ACCEPTED_STARTED,
                false,
                InformationRequestErrorCatalog.STATE_INVALID,
            ),
            CredentialCommandCutoffCase(
                "ended Exchange",
                InformationRequestState.ISSUED,
                ExchangeStatus.ENDED,
                false,
                InformationRequestErrorCatalog.PARENT_STATE_INVALID,
            ),
            CredentialCommandCutoffCase(
                "rejected Exchange",
                InformationRequestState.ISSUED,
                ExchangeStatus.REJECTED,
                false,
                InformationRequestErrorCatalog.PARENT_STATE_INVALID,
            ),
            CredentialCommandCutoffCase(
                "rescinded Exchange",
                InformationRequestState.ISSUED,
                ExchangeStatus.RESCINDED,
                false,
                InformationRequestErrorCatalog.PARENT_STATE_INVALID,
            ),
            CredentialCommandCutoffCase(
                "deleted Exchange",
                InformationRequestState.ISSUED,
                ExchangeStatus.ACCEPTED_STARTED,
                true,
                InformationRequestErrorCatalog.PARENT_STATE_INVALID,
            ),
        )
    }
}
