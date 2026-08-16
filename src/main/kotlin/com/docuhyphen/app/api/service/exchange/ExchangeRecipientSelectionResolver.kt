package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.OrganizationGroupNotFoundException
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.ExchangeRecipientType
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.Person
import com.docuhyphen.app.api.resource.model.ExchangeRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.ExternalEmailRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.InternalGroupRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.PersonalGroupRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.RegisteredUserRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.TrustedGroupRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.TrustedPersonRecipientSelectionRequest
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.identity.ExternalIdentityResolutionService
import com.docuhyphen.app.api.service.identity.ExternalIdentityResolutionService.PreparedPersonResolution
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.organization.TrustedGroupValidation
import com.docuhyphen.app.api.service.organization.TrustedRecipientAuditService
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
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

@ApplicationScoped
class ExchangeRecipientSelectionResolver @Inject constructor(
    private val appUserService: AppUserService,
    private val organizationGroupService: OrganizationGroupService,
    private val organizationExchangePolicyService: OrganizationExchangePolicyService,
    private val trustedRecipientValidationService: TrustedRecipientValidationService,
    private val externalIdentityResolutionService: ExternalIdentityResolutionService,
    private val trustedRecipientAuditService: TrustedRecipientAuditService,
)
{
    fun resolve(
        selection: ExchangeRecipientSelectionRequest,
        initiator: AppUser,
        activeOrganizationId: UUID?,
    ): ResolvedExchangeRecipientSelection = when (selection)
    {
        is RegisteredUserRecipientSelectionRequest -> resolveRegisteredUser(selection, initiator, activeOrganizationId)
        is ExternalEmailRecipientSelectionRequest -> resolveExternalEmail(selection, initiator, activeOrganizationId)
        is InternalGroupRecipientSelectionRequest -> resolveInternalGroup(selection, initiator, activeOrganizationId)
        is PersonalGroupRecipientSelectionRequest -> resolvePersonalGroup(selection, initiator, activeOrganizationId)
        is TrustedGroupRecipientSelectionRequest -> resolveTrustedGroup(selection, initiator, activeOrganizationId)
        is TrustedPersonRecipientSelectionRequest -> resolveTrustedPerson(selection, initiator, activeOrganizationId)
    }

    private fun resolveRegisteredUser(
        selection: RegisteredUserRecipientSelectionRequest,
        initiator: AppUser,
        activeOrganizationId: UUID?,
    ): ResolvedExchangeRecipientSelection
    {
        val appUserId = parseId(selection.appUserId, "Recipient user")
        require(appUserId != initiator.id) { "Recipient and Initiator cannot be the same" }
        val appUser = appUserService.getById(appUserId)
            ?: throw AppUserNotFoundException("Recipient not found")
        organizationExchangePolicyService.assertCanShareWithUser(activeOrganizationId, initiator.id, appUser.id)
        return ResolvedExchangeRecipientSelection(
            ExchangeRecipientType.APP_USER,
            ExchangeRecipientSelectionType.REGISTERED_USER,
            appUser = appUser,
        )
    }

    private fun resolveExternalEmail(
        selection: ExternalEmailRecipientSelectionRequest,
        initiator: AppUser,
        activeOrganizationId: UUID?,
    ): ResolvedExchangeRecipientSelection
    {
        val appUser = appUserService.getAppUserByEmail(selection.email)
            ?.applyRecipientName(selection.firstName, selection.lastName)
            ?: AppUser().apply {
                isTemporary = true
                email = selection.email
                isActive = false
            }.applyRecipientName(selection.firstName, selection.lastName)
        require(appUser.id != initiator.id) { "Recipient and Initiator cannot be the same" }
        organizationExchangePolicyService.assertCanShareWithUser(
            activeOrganizationId,
            initiator.id,
            appUser.takeIf { it.isTemporary != true }?.id,
        )
        return ResolvedExchangeRecipientSelection(
            ExchangeRecipientType.EMAIL,
            ExchangeRecipientSelectionType.EXTERNAL_EMAIL,
            appUser = appUser,
        )
    }

    private fun resolveInternalGroup(
        selection: InternalGroupRecipientSelectionRequest,
        initiator: AppUser,
        activeOrganizationId: UUID?,
    ): ResolvedExchangeRecipientSelection
    {
        val organizationId = activeOrganizationId
            ?: throw IllegalArgumentException("An active organization is required for an organization group")
        val group = requireGroup(selection.groupId)
        require(group.scope == PrincipalGroupScope.ORG && group.ownerOrganizationId == organizationId) {
            "Recipient group is not owned by the active organization"
        }
        organizationExchangePolicyService.assertCanShareWithGroup(activeOrganizationId, initiator.id, group)
        return ResolvedExchangeRecipientSelection(
            ExchangeRecipientType.GROUP,
            ExchangeRecipientSelectionType.INTERNAL_GROUP,
            group = group,
            targetOrganizationId = organizationId,
        )
    }

    private fun resolvePersonalGroup(
        selection: PersonalGroupRecipientSelectionRequest,
        initiator: AppUser,
        activeOrganizationId: UUID?,
    ): ResolvedExchangeRecipientSelection
    {
        val group = requireGroup(selection.groupId)
        require(group.scope == PrincipalGroupScope.PERSONAL && group.ownerAppUserId == initiator.id) {
            "Recipient group is not owned by the initiator"
        }
        organizationExchangePolicyService.assertCanShareWithGroup(activeOrganizationId, initiator.id, group)
        return ResolvedExchangeRecipientSelection(
            ExchangeRecipientType.GROUP,
            ExchangeRecipientSelectionType.PERSONAL_GROUP,
            group = group,
        )
    }

    private fun resolveTrustedGroup(
        selection: TrustedGroupRecipientSelectionRequest,
        initiator: AppUser,
        activeOrganizationId: UUID?,
    ): ResolvedExchangeRecipientSelection
    {
        var targetOrganizationId: UUID? = null
        var groupId: UUID? = null
        val validation = try
        {
            val callerOrganizationId = activeOrganizationId
                ?: throw IllegalArgumentException("An active organization is required for a trusted group")
            val parsedTargetOrganizationId = parseId(selection.organizationId, "Trusted Organization")
            val parsedGroupId = parseId(selection.groupId, "Trusted group")
            targetOrganizationId = parsedTargetOrganizationId
            groupId = parsedGroupId
            trustedRecipientValidationService.validateGroupSelection(
                callerOrganizationId,
                parsedTargetOrganizationId,
                parsedGroupId,
            )
        }
        catch (exception: Exception)
        {
            trustedRecipientAuditService.recordValidationDenied(
                initiator.id,
                activeOrganizationId,
                targetOrganizationId,
                ExchangeRecipientSelectionType.TRUSTED_GROUP,
                groupId,
            )
            throw exception
        }
        trustedRecipientAuditService.recordValidationAllowed(
            initiator.id,
            requireNotNull(activeOrganizationId),
            validation.targetOrganization.id,
            ExchangeRecipientSelectionType.TRUSTED_GROUP,
            validation.group.id,
        )
        return ResolvedExchangeRecipientSelection(
            ExchangeRecipientType.GROUP,
            ExchangeRecipientSelectionType.TRUSTED_GROUP,
            group = validation.group,
            targetOrganizationId = validation.targetOrganization.id,
            trustedGroupValidation = validation,
        )
    }

    private fun resolveTrustedPerson(
        selection: TrustedPersonRecipientSelectionRequest,
        initiator: AppUser,
        activeOrganizationId: UUID?,
    ): ResolvedExchangeRecipientSelection
    {
        val prepared = try
        {
            val callerOrganizationId = activeOrganizationId
                ?: throw IllegalArgumentException("An active organization is required for a trusted person")
            val resolutionId = parseId(selection.resolutionId, "Trusted member verification")
            externalIdentityResolutionService.prepareForInitiation(
                resolutionId,
                initiator.id,
                callerOrganizationId,
            ).also {
                require(it.appUser.id != initiator.id) { "Recipient and Initiator cannot be the same" }
            }
        }
        catch (exception: Exception)
        {
            trustedRecipientAuditService.recordValidationDenied(
                initiator.id,
                activeOrganizationId,
                targetOrganizationId = null,
                ExchangeRecipientSelectionType.TRUSTED_PERSON,
                subjectId = null,
            )
            throw exception
        }
        trustedRecipientAuditService.recordValidationAllowed(
            initiator.id,
            requireNotNull(activeOrganizationId),
            prepared.resolution.targetOrganizationId,
            ExchangeRecipientSelectionType.TRUSTED_PERSON,
            prepared.appUser.id,
        )
        return ResolvedExchangeRecipientSelection(
            ExchangeRecipientType.APP_USER,
            ExchangeRecipientSelectionType.TRUSTED_PERSON,
            appUser = prepared.appUser,
            targetOrganizationId = prepared.resolution.targetOrganizationId,
            preparedPersonResolution = prepared,
        )
    }

    private fun requireGroup(groupId: String): PrincipalGroup
    {
        val id = parseId(groupId, "Recipient group")
        val group = organizationGroupService.getById(id.toString())
            ?: throw OrganizationGroupNotFoundException("Recipient group not found")
        require(group.isActive) { "Recipient group is inactive" }
        return group
    }

    private fun parseId(value: String, label: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrElse { throw IllegalArgumentException("$label id is invalid") }

    private fun AppUser.applyRecipientName(firstName: String, lastName: String): AppUser = apply {
        if (isTemporary && person == null)
        {
            person = Person().apply {
                this.firstName = firstName.trim()
                this.lastName = lastName.trim()
            }
        }
    }
}
