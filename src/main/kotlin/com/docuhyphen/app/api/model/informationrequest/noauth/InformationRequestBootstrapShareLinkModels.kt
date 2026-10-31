package com.docuhyphen.app.api.model.informationrequest.noauth

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import java.sql.Timestamp
import java.util.*

data class IssueInformationRequestBootstrapShareLinkCommand(
    val requestId: UUID,
    val partyId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val expiresAt: Timestamp? = null,
    val maxUses: Int? = null,
)

data class RotateInformationRequestBootstrapShareLinkCommand(
    val requestId: UUID,
    val shareLinkId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class ReplaceInformationRequestBootstrapShareLinkCommand(
    val requestId: UUID,
    val shareLinkId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val expiresAt: Timestamp? = null,
    val maxUses: Int? = null,
)

data class RevokeInformationRequestBootstrapShareLinkCommand(
    val requestId: UUID,
    val shareLinkId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestBootstrapShareLinkIssuance(
    val shareLink: ShareLink,
    val rawToken: String,
    val partyETag: String,
)

data class InformationRequestBootstrapShareLinkRevocation(
    val shareLink: ShareLink,
    val partyETag: String,
)

data class LockedInformationRequestBootstrapLink(
    val request: InformationRequest,
    val shareLink: ShareLink,
    val party: InformationRequestParty,
    val parent: Exchange,
)
