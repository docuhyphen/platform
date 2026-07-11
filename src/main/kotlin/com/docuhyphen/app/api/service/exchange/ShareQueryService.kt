package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.dto.SessionAccessEntryDto
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.ShareRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Read-side over the unified [Share] model that powers the "manage access" view.
 * Additive, it does not change existing authorization; it surfaces the access list the new
 * model already maintains via dual-write, so the redesigned access-management UX can be built
 * before the legacy read paths are retired at cutover.
 */
@ApplicationScoped
class ShareQueryService @Inject constructor(
    private val shareRepository: ShareRepository,
    private val appUserRepository: AppUserRepository,
    private val principalGroupRepository: PrincipalGroupRepository,
    private val externalParticipantRepository: ExternalParticipantRepository,
)
{
    /** All access entries (any status) for a exchange, newest grant first.
     *  INHERITED_FROM_GROUP rows are internal materialisation details and are excluded;
     *  the group's own DIRECT share row already represents them in the UI. */
    fun getSessionAccessView(exchangeId: UUID): List<SessionAccessEntryDto> =
        shareRepository.findAllByResource(ResourceType.EXCHANGE, exchangeId)
            .filter { it.source != ShareSource.INHERITED_FROM_GROUP }
            .map { it.toAccessEntry() }

    private fun Share.toAccessEntry(): SessionAccessEntryDto =
        SessionAccessEntryDto(
            shareId = id,
            principalKind = principalKind.name,
            principalId = principalId,
            displayName = resolveDisplayName(principalKind, principalId),
            roleName = roleName,
            source = source.name,
            status = status.name,
            grantedByAppUserId = grantedByAppUserId,
            grantedAt = grantedAt,
            expiresAt = expiresAt,
            constraintsJson = constraintsJson,
        )

    private fun resolveDisplayName(kind: PrincipalKind, id: UUID): String? =
        when (kind)
        {
            PrincipalKind.USER ->
            {
                val user = appUserRepository.findById(id)
                val fullName = "${user?.person?.firstName.orEmpty()} ${user?.person?.lastName.orEmpty()}".trim()
                fullName.ifBlank { user?.email }
            }
            PrincipalKind.PRINCIPAL_GROUP -> principalGroupRepository.findById(id)?.name
            PrincipalKind.PARTICIPANT -> externalParticipantRepository.findById(id)?.email
            else -> null
        }
}
