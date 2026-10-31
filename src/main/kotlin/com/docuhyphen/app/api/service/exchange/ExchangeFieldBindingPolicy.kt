package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.fields.FieldsAccessContext
import com.docuhyphen.app.api.model.fields.FieldsResourceRef
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.fields.AudienceFieldBindingPolicy
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class ExchangeFieldBindingPolicy @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val organizationMembershipService: OrganizationMembershipService,
) : AudienceFieldBindingPolicy()
{
    override fun isExternalCaller(resource: FieldsResourceRef, access: FieldsAccessContext): Boolean
    {
        val principal = access.principal
        return when (principal.kind)
        {
            PrincipalKind.PARTICIPANT, PrincipalKind.PUBLIC_LINK -> true
            PrincipalKind.USER ->
            {
                val exchange = exchangeRepository.findById(resource.resourceId) ?: return true
                val ownerOrgId = exchange.ownerOrganizationId
                if (ownerOrgId != null)
                {
                    !organizationMembershipService.isMember(principal.id, ownerOrgId)
                }
                else
                {
                    exchange.ownerUserId != principal.id
                }
            }

            else -> false
        }
    }
}
