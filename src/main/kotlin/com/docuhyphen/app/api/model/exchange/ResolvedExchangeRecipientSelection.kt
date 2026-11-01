package com.docuhyphen.app.api.model.exchange

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.ExchangeRecipientType
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.identity.PreparedPersonResolution
import com.docuhyphen.app.api.model.organization.TrustedGroupValidation
import java.util.UUID

data class ResolvedExchangeRecipientSelection(
    val recipientType: ExchangeRecipientType,
    val selectionType: ExchangeRecipientSelectionType,
    val appUser: AppUser? = null,
    val group: PrincipalGroup? = null,
    val targetOrganizationId: UUID? = null,
    val trustedGroupValidation: TrustedGroupValidation? = null,
    val preparedPersonResolution: PreparedPersonResolution? = null,
)
{
    val principalKind: PrincipalKind
        get() = if (group == null) PrincipalKind.USER else PrincipalKind.PRINCIPAL_GROUP

    val principalId: UUID
        get() = group?.id ?: requireNotNull(appUser).id
}
