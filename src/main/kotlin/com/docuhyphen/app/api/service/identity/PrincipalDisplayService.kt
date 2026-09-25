package com.docuhyphen.app.api.service.identity

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.identity.PrincipalDisplay
import com.docuhyphen.app.api.repository.exchange.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.user.AppUserRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class PrincipalDisplayService @Inject constructor(
    private val appUserRepository: AppUserRepository,
    private val principalGroupRepository: PrincipalGroupRepository,
    private val externalParticipantRepository: ExternalParticipantRepository,
)
{
    fun display(principal: PrincipalRef): PrincipalDisplay =
        when (principal.kind)
        {
            PrincipalKind.USER -> appUserRepository.findById(principal.id)
                ?.let { user ->
                    val fullName = listOfNotNull(user.person?.firstName, user.person?.lastName)
                        .joinToString(" ")
                        .trim()
                    PrincipalDisplay(name = fullName.ifBlank { user.email }, email = user.email)
                }

            PrincipalKind.PRINCIPAL_GROUP -> principalGroupRepository.findById(principal.id)
                ?.let { group -> PrincipalDisplay(name = group.name, email = null) }

            PrincipalKind.PARTICIPANT -> externalParticipantRepository.findById(principal.id)
                ?.let { participant -> PrincipalDisplay(name = participant.email, email = participant.email) }

            else -> null
        } ?: PrincipalDisplay.UNKNOWN
}
