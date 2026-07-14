package com.docuhyphen.app.api.service.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.Optional

/**
 * OAuth provider, OIDC validation, application-token, audit, and SCIM/directory
 * configuration properties.
 *
 * Extracted from [ConfigurationService] to keep each CDI bean's generated constructor
 * well under the JVM 64 KB method bytecode limit (Quarkus ArC generates one Supplier<T>
 * per @ConfigProperty constructor parameter in the *_Bean wrapper class).
 */
@ApplicationScoped
class OAuthConfigService @Inject constructor(

    // --- Microsoft OAuth ---
    // Optional<String>: SmallRye Config 3.x converts "" → null via BuiltInConverter and
    // rejects null injection into a plain String parameter (Kotlin nullable annotations
    // are invisible to SmallRye). Optional<String> is the MicroProfile-blessed way to
    // declare an optional config value,  empty/missing both become Optional.empty().
    // Getters below coerce to "" so all 42 downstream callers continue to see non-null Strings.
    @ConfigProperty(name = "app.oauth.microsoft.client-id")
    private val microsoftOAuthClientId: Optional<String>,

    @ConfigProperty(name = "app.oauth.microsoft.client-secret")
    private val microsoftOAuthClientSecret: Optional<String>,

    @ConfigProperty(name = "app.oauth.microsoft.tenant-id", defaultValue = "common")
    private val microsoftOAuthTenantId: String,

    @ConfigProperty(name = "app.oauth.microsoft.redirect-uri")
    private val microsoftOAuthRedirectUri: Optional<String>,

    // --- Google OAuth ---
    @ConfigProperty(name = "app.oauth.google.client-id")
    private val googleOAuthClientId: Optional<String>,

    @ConfigProperty(name = "app.oauth.google.client-secret")
    private val googleOAuthClientSecret: Optional<String>,

    @ConfigProperty(name = "app.oauth.google.redirect-uri")
    private val googleOAuthRedirectUri: Optional<String>,

    // --- OIDC validation ---
    @ConfigProperty(name = "app.oidc.validation.require-azp-when-multi-aud", defaultValue = "true")
    private val oidcRequireAzpWhenMultiAudConfig: Boolean,

    @ConfigProperty(name = "app.oidc.validation.required-claims.google", defaultValue = "sub,email,email_verified,iss,aud,exp,iat,nonce")
    private val oidcRequiredClaimsGoogleConfig: String,

    @ConfigProperty(name = "app.oidc.validation.required-claims.microsoft", defaultValue = "sub,oid,tid,iss,aud,exp,iat,nonce")
    private val oidcRequiredClaimsMicrosoftConfig: String,

    // --- Application tokens ---
    @ConfigProperty(name = "app.auth.application.default-scopes", defaultValue = "application:api")
    private val applicationTokenDefaultScopesConfig: String,

    @ConfigProperty(name = "app.auth.application.required-scope", defaultValue = "application:api")
    private val applicationTokenRequiredScopeConfig: String,

    @ConfigProperty(name = "app.auth.application.integration-scope", defaultValue = "application:integration")
    private val applicationTokenIntegrationScopeConfig: String,

    @ConfigProperty(name = "app.auth.application.service-scope", defaultValue = "application:service")
    private val applicationTokenServiceScopeConfig: String,

    @ConfigProperty(name = "app.auth.application.allowed-endpoint-prefixes")
    private val applicationTokenAllowedEndpointPrefixesConfig: String,

    // --- SCIM / Directory ---
    @ConfigProperty(name = "app.scim.bearer-token")
    private val scimBearerTokenConfig: Optional<String>,

    @ConfigProperty(name = "app.idp.directory.lookup.max-results", defaultValue = "100")
    private val directoryLookupMaxResultsConfig: Int,

    @ConfigProperty(name = "app.idp.directory.lookup.min-query-length", defaultValue = "3")
    private val directoryLookupMinQueryLengthConfig: Int,
)
{
    fun getMicrosoftOAuthClientId(): String = microsoftOAuthClientId.orElse("")
    fun getMicrosoftOAuthClientSecret(): String = microsoftOAuthClientSecret.orElse("")
    fun getMicrosoftOAuthTenantId(): String = microsoftOAuthTenantId
    fun getMicrosoftOAuthRedirectUri(): String = microsoftOAuthRedirectUri.orElse("")

    fun getGoogleOAuthClientId(): String = googleOAuthClientId.orElse("")
    fun getGoogleOAuthClientSecret(): String = googleOAuthClientSecret.orElse("")
    fun getGoogleOAuthRedirectUri(): String = googleOAuthRedirectUri.orElse("")

    fun isOidcRequireAzpWhenMultiAudEnabled(): Boolean = oidcRequireAzpWhenMultiAudConfig
    fun getOidcRequiredClaimsGoogle(): Set<String> =
        oidcRequiredClaimsGoogleConfig.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    fun getOidcRequiredClaimsMicrosoft(): Set<String> =
        oidcRequiredClaimsMicrosoftConfig.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()

    fun getApplicationTokenDefaultScopes(): Set<String> =
        applicationTokenDefaultScopesConfig.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    fun getApplicationTokenRequiredScope(): String = applicationTokenRequiredScopeConfig.trim().ifBlank { "application:api" }
    fun getApplicationTokenIntegrationScope(): String = applicationTokenIntegrationScopeConfig.trim().ifBlank { "application:integration" }
    fun getApplicationTokenServiceScope(): String = applicationTokenServiceScopeConfig.trim().ifBlank { "application:service" }
    fun getApplicationTokenAllowedEndpointPrefixes(): Set<String> = applicationTokenAllowedEndpointPrefixesConfig
        .split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { if (it.startsWith('/')) it.lowercase() else "/${it.lowercase()}" }
        .toSet()


    fun getScimBearerToken(): String = scimBearerTokenConfig.orElse("")
    fun getDirectoryLookupMaxResults(): Int = directoryLookupMaxResultsConfig
    fun getDirectoryLookupMinQueryLength(): Int = directoryLookupMinQueryLengthConfig
}
