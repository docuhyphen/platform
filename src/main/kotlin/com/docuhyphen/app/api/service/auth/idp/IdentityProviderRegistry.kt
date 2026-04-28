package com.docuhyphen.app.api.service.auth.idp

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

@ApplicationScoped
class IdentityProviderRegistry @Inject constructor(
    private val providers: Instance<IdentityProviderStrategy>,
)
{
    private val providerMap: Map<IdentityProviderType, IdentityProviderStrategy> by lazy {
        providers.associateBy { it.getProviderType() }
    }

    fun getProvider(type: IdentityProviderType): IdentityProviderStrategy
    {
        return providerMap[type]
            ?: throw IllegalArgumentException("No identity provider registered for type: $type")
    }

    fun getAllProviders(): Collection<IdentityProviderStrategy> = providerMap.values
}

