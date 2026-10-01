package com.docuhyphen.app.api.model.informationrequest.party

import com.docuhyphen.app.api.model.entity.InformationRequestDelegatedAuthority
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import java.sql.Timestamp
import java.util.*

data class GrantInformationRequestDelegatedAuthorityCommand(
    val requestId: UUID,
    val assignedPartyId: UUID,
    val delegatePrincipal: PrincipalRef,
    val requirementId: UUID? = null,
    val authorityInstrumentRef: String? = null,
    val effectiveAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class RevokeInformationRequestDelegatedAuthorityCommand(
    val requestId: UUID,
    val authorityId: UUID,
    val reason: String? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestDelegatedAuthorityResult(
    val authority: InformationRequestDelegatedAuthority,
    val authoritiesETag: String,
)
