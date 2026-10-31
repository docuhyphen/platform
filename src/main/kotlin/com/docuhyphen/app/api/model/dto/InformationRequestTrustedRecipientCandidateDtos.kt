package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class InformationRequestTrustedRecipientCandidateDto(
    @Serializable(with = UUIDSerializer::class) val exchangeRecipientId: UUID,
    val selectionType: ExchangeRecipientSelectionType,
    val principalKind: PrincipalKind,
    @Serializable(with = UUIDSerializer::class) val principalId: UUID,
    val label: String,
    @Serializable(with = UUIDSerializer::class) val targetOrganizationId: UUID? = null,
    val targetOrganizationName: String? = null,
    val trustSuspended: Boolean,
)
