package com.docuhyphen.app.api.service.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

/**
 * Configuration for the crypto-shredding primitive
 * (`com.docuhyphen.app.api.service.audit.identity.AuditIdentityVaultService`). Same local-first
 * default as [AuditArchiveConfigService]: a local-filesystem master key works out of the box in
 * dev/CI, and `app.audit.identity-vault.provider=aws` switches to a single AWS Secrets Manager
 * secret holding the base64 AES-256 master key - no per-subject secret, no KMS.
 */
@ApplicationScoped
class AuditIdentityVaultConfigService @Inject constructor(

    @ConfigProperty(name = "app.audit.identity-vault.provider", defaultValue = "local")
    private val providerConfig: String,

    @ConfigProperty(name = "app.audit.identity-vault.local.directory", defaultValue = "local-development-resources/logs/audit-identity-vault")
    private val localDirectoryConfig: String,

    @ConfigProperty(name = "app.audit.identity-vault.secret-id", defaultValue = "docuhyphen-audit-identity-vault-master-key")
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
