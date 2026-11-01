package com.docuhyphen.app.api.service.identity

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.identity.PrincipalDisplay
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import com.docuhyphen.app.api.service.user.AppUserLookupService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class PrincipalDisplayService @Inject constructor(
    private val appUsers: AppUserLookupService,
    private val principalGroups: PrincipalGroupService,
    private val externalParticipants: ExternalParticipantService,
)
{
    fun display(principal: PrincipalRef): PrincipalDisplay =
        when (principal.kind)
        {
            PrincipalKind.USER -> appUsers.findById(principal.id)
                ?.let { user ->
                    val fullName = listOfNotNull(user.person?.firstName, user.person?.lastName)
                        .joinToString(" ")
                        .trim()
                    PrincipalDisplay(name = fullName.ifBlank { user.email }, email = user.email)
                }

            PrincipalKind.PRINCIPAL_GROUP -> principalGroups.findById(principal.id)
                ?.let { group -> PrincipalDisplay(name = group.name, email = null) }

            PrincipalKind.PARTICIPANT -> externalParticipants.findById(principal.id)
                ?.let { participant -> PrincipalDisplay(name = participant.email, email = participant.email) }

            else -> null
        } ?: PrincipalDisplay.UNKNOWN

    fun userLabel(userId: UUID): String? =
        appUsers.findById(userId)?.let { user ->
            val fullName = listOfNotNull(user.person?.firstName, user.person?.lastName)
                .joinToString(" ")
                .trim()
            if (fullName.isNotEmpty()) "$fullName <${user.email}>" else user.email
        }
}
