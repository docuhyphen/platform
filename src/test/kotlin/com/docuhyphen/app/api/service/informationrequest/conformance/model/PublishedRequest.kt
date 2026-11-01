package com.docuhyphen.app.api.service.informationrequest.conformance.model

import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonField
import java.util.*

internal data class PublishedRequest(
    val requestId: UUID,
    val exchangeId: UUID,
    val definitionId: UUID,
    val templateVersionId: UUID,
    val ownerId: UUID,
    val users: Map<String, UUID>,
    val parties: Map<String, UUID>,
    val fields: Map<String, WalkingSkeletonField>,
    val participants: Set<String> = emptySet(),
    val subjects: Map<String, UUID> = emptyMap(),
) {
    fun access(party: String): RequestAccessContext =
        RequestAccessContext(principal(party), AuthorizationContext(sessionRef = "${party.lowercase()}-session"))

    fun principal(party: String): PrincipalRef =
        if (party in participants) PrincipalRef.participant(users.getValue(party)) else PrincipalRef.user(
            users.getValue(
                party
            )
        )

    val owner: RequestAccessContext
        get() = RequestAccessContext(
            PrincipalRef.user(ownerId),
            AuthorizationContext(sessionRef = "owner-session")
        )
}
