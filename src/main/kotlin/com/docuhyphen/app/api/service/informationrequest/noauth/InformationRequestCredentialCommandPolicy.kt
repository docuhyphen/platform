package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestPolicyDecision
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.noauth.LockedInformationRequestBootstrapLink
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionMatrix

fun requireCredentialCommandAllowed(link: LockedInformationRequestBootstrapLink, mutation: InformationRequestMutation) =
    requireCredentialCommandAllowed(
        InformationRequestParentSnapshot(link.parent.status, link.parent.isDeleted, lockedForUpdate = true),
        link.request.state,
        mutation,
    )

fun requireCredentialCommandAllowed(
    parent: InformationRequestParentSnapshot,
    requestState: InformationRequestState?,
    mutation: InformationRequestMutation,
) {
    val decision = InformationRequestTransitionMatrix.canMutate(parent, requestState, mutation)
    if (decision is InformationRequestPolicyDecision.Deny) {
        throw InformationRequestLifecycleException(
            decision.reasonCode,
            "This request no longer sends verification codes or opens new sessions",
        )
    }
}
