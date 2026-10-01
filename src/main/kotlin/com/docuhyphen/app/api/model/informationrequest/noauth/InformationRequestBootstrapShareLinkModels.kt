package com.docuhyphen.app.api.model.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.command.CommandPrecondition
import java.sql.Timestamp
import java.util.UUID

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
)
