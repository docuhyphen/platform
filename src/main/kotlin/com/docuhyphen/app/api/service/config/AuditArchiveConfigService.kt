package com.docuhyphen.app.api.service.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

/**
 * Configuration for Phase 4 (Immutable WORM Archive + Signed Segments + Verification) of
 * `AUDIT-ARCHITECTURE-IMPLEMENTATION.md`. Kept as its own bean (not folded into
 * [OAuthConfigService]) per that class's own doc comment about keeping each `@ConfigProperty`
 * constructor well under the JVM 64 KB bytecode limit.
 *
 * Storage and signing each default to a local-filesystem provider so the archiver/verifier/
 * scheduler work out of the box in dev/CI without AWS credentials (mirrors the existing
 * `file.storage.service`/[AuthAuditWormSink]-style local-first defaults elsewhere in this
 * codebase); set the `*.type`/`*.provider` properties to `aws` to use S3 + Secrets Manager in an
 * environment that has them configured. No new AWS service type is introduced either way - see
 * the Phase 4 cost constraint in `AUDIT-ARCHITECTURE-IMPLEMENTATION.md`.
 */
@ApplicationScoped
class AuditArchiveConfigService @Inject constructor(

    @ConfigProperty(name = "app.audit.archive.enabled", defaultValue = "true")
    private val archiveEnabledConfig: Boolean,

    @ConfigProperty(name = "app.audit.archive.storage.type", defaultValue = "local")
    private val storageTypeConfig: String,

    @ConfigProperty(name = "app.audit.archive.signing.provider", defaultValue = "local")
    private val signingProviderConfig: String,

    @ConfigProperty(name = "app.audit.archive.local.directory", defaultValue = "logs/audit-archive")
    private val localDirectoryConfig: String,

    @ConfigProperty(name = "app.audit.archive.local.signing-directory", defaultValue = "logs/audit-archive-keys")
    private val localSigningDirectoryConfig: String,

    @ConfigProperty(name = "app.audit.archive.bucket", defaultValue = "")
    private val bucketConfig: String,

    @ConfigProperty(name = "app.audit.archive.region", defaultValue = "us-east-1")
    private val regionConfig: String,

    @ConfigProperty(name = "app.audit.archive.signing.secret-id", defaultValue = "docuhyphen-audit-archive-signing-key")
    private val signingSecretIdConfig: String,

    @ConfigProperty(name = "app.audit.archive.signing.region", defaultValue = "us-east-1")
    private val signingRegionConfig: String,

    @ConfigProperty(name = "app.audit.archive.segment-size", defaultValue = "500")
    private val segmentSizeConfig: Int,

    @ConfigProperty(name = "app.audit.archive.archive-every", defaultValue = "5m")
    private val archiveEveryConfig: String,

    @ConfigProperty(name = "app.audit.archive.verify-every", defaultValue = "1h")
    private val verifyEveryConfig: String,

    @ConfigProperty(name = "app.audit.archive.reverify-after-hours", defaultValue = "24")
    private val reverifyAfterHoursConfig: Int,
)
{
    fun isArchiveEnabled(): Boolean = archiveEnabledConfig
    fun getStorageType(): String = storageTypeConfig.trim().lowercase().ifBlank { "local" }
    fun getSigningProvider(): String = signingProviderConfig.trim().lowercase().ifBlank { "local" }
    fun getLocalDirectory(): String = localDirectoryConfig
    fun getLocalSigningDirectory(): String = localSigningDirectoryConfig
    fun getBucket(): String = bucketConfig
    fun getRegion(): String = regionConfig
    fun getSigningSecretId(): String = signingSecretIdConfig
    fun getSigningRegion(): String = signingRegionConfig
    fun getSegmentSize(): Int = if (segmentSizeConfig > 0) segmentSizeConfig else 500
    fun getArchiveEvery(): String = archiveEveryConfig
    fun getVerifyEvery(): String = verifyEveryConfig
    fun getReverifyAfterHours(): Int = if (reverifyAfterHoursConfig > 0) reverifyAfterHoursConfig else 24
}
