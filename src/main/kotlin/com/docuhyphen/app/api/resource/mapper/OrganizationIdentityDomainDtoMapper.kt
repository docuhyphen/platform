package com.docuhyphen.app.api.resource.mapper

import com.docuhyphen.app.api.model.entity.OrganizationIdentityDomain
import com.docuhyphen.app.api.resource.model.OrganizationIdentityDomainResponse
import com.docuhyphen.app.api.service.identity.OrganizationIdentityDomainService
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class OrganizationIdentityDomainDtoMapper
{
    fun toResponse(domain: OrganizationIdentityDomain): OrganizationIdentityDomainResponse
    {
        return OrganizationIdentityDomainResponse(
            id = domain.id.toString(),
            organizationId = domain.organization?.id?.toString().orEmpty(),
            domain = domain.domain,
            status = domain.status.name,
            dnsRecordName = "${OrganizationIdentityDomainService.DNS_RECORD_PREFIX}.${domain.domain}",
            dnsRecordValue = OrganizationIdentityDomainService.DNS_VALUE_PREFIX + domain.verificationToken,
            createdDate = domain.createdDate.toString(),
            verifiedDate = domain.verifiedDate?.toString(),
        )
    }
}
