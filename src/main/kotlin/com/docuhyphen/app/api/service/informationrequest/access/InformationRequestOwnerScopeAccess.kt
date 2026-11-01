package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.recordpreservation.RecordOwnerScopeAccess
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestOwnerScopeAccess @Inject constructor(
    private val records: RecordOwnerScopeAccess,
    private val authorizationContextFactory: AuthorizationContextFactory,
)
{
    fun currentOwner(): InformationRequestOwnerRef
    {
        val owner = records.currentOwner()
        return InformationRequestOwnerRef(
            if (owner.kind == RecordOwnerKind.ORGANIZATION) InformationRequestOwnerType.ORGANIZATION else InformationRequestOwnerType.USER,
            requireNotNull(owner.id),
        )
    }

    fun requireAccess(owner: InformationRequestOwnerRef, action: Action): RequestAccessContext
    {
        val principal = requireUser()
        if (permits(principal, owner, action)) return RequestAccessContext(
            principal,
            authorizationContextFactory.currentContext()
        )
        throw ForbiddenException("Access denied to this owner's Information Request configuration")
    }

    fun permits(principal: PrincipalRef, owner: InformationRequestOwnerRef, action: Action): Boolean =
        records.permits(principal, recordOwnerOf(owner), action)

    fun permits(
        principal: PrincipalRef,
        owner: InformationRequestOwnerRef,
        action: Action,
        context: AuthorizationContext,
    ): Boolean = records.permits(principal, recordOwnerOf(owner), action, context)

    fun requireUser(): PrincipalRef = records.requireUser()

    companion object
    {
        fun recordOwnerOf(owner: InformationRequestOwnerRef): RecordOwnerRef = when (owner.ownerType)
        {
            InformationRequestOwnerType.ORGANIZATION -> RecordOwnerRef.organization(owner.ownerId)
            InformationRequestOwnerType.USER -> RecordOwnerRef.user(owner.ownerId)
        }
    }
}
