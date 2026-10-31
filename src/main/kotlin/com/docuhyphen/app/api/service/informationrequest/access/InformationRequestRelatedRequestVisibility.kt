package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import java.util.*

object InformationRequestRelatedRequestVisibility {
    fun of(gate: InformationRequestMutationGate, access: RequestAccessContext): (UUID) -> Boolean {
        val decisions = mutableMapOf<UUID, Boolean>()
        return { relatedRequestId ->
            decisions.getOrPut(relatedRequestId) {
                gate.permitsRequest(access, Action.INFORMATION_REQUEST_VIEW, relatedRequestId)
            }
        }
    }
}
