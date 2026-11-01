package com.docuhyphen.app.api.service.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class AuditIdentityVaultConfigService @Inject constructor(

    @ConfigProperty(name = "app.audit.identity-vault.provider", defaultValue = "local")
    private val providerConfig: String,

    @ConfigProperty(
        name = "app.audit.identity-vault.local.directory",
        defaultValue = "local-development-resources/logs/audit-identity-vault"
    )
    private val localDirectoryConfig: String,

    @ConfigProperty(
        name = "app.audit.identity-vault.secret-id",
        defaultValue = "docuhyphen-audit-identity-vault-master-key"
    )
    private val secretIdConfig: String,

    @ConfigProperty(name = "app.audit.identity-vault.region", defaultValue = "us-east-1")
    private val regionConfig: String,
)
{
    fun getProvider(): String = providerConfig.trim().lowercase().ifBlank { "local" }
    fun getLocalDirectory(): String = localDirectoryConfig
    fun getSecretId(): String = secretIdConfig
    fun getRegion(): String = regionConfig
}
