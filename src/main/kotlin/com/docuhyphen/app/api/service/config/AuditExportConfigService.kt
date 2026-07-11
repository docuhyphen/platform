package com.docuhyphen.app.api.service.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

/**
 * Configuration for verifiable evidence exports. Kept as its own bean (mirrors
 * [AuditArchiveConfigService]'s reasoning about `@ConfigProperty` constructor bytecode size).
 *
 * Dual control defaults to required: an export only reaches `BUILDING` once
 * [getRequiredApprovals] distinct approvers (never the requester) have approved it. Set
 * `app.audit.export.dual-control-required=false` only for a lower-trust dev/test environment.
 */
@ApplicationScoped
class AuditExportConfigService @Inject constructor(

    @ConfigProperty(name = "app.audit.export.dual-control-required", defaultValue = "true")
    private val dualControlRequiredConfig: Boolean,

    @ConfigProperty(name = "app.audit.export.required-approvals", defaultValue = "1")
    private val requiredApprovalsConfig: Int,

    @ConfigProperty(name = "app.audit.export.max-range-days", defaultValue = "366")
    private val maxRangeDaysConfig: Int,

    @ConfigProperty(name = "app.audit.export.download-lifetime-hours", defaultValue = "72")
    private val downloadLifetimeHoursConfig: Int,

    @ConfigProperty(name = "app.audit.export.default-download-limit", defaultValue = "5")
    private val defaultDownloadLimitConfig: Int,

    @ConfigProperty(name = "app.audit.export.build-every", defaultValue = "1m")
    private val buildEveryConfig: String,

    @ConfigProperty(name = "app.audit.export.expire-every", defaultValue = "15m")
    private val expireEveryConfig: String,
)
{
    fun isDualControlRequired(): Boolean = dualControlRequiredConfig
    fun getRequiredApprovals(): Int = if (requiredApprovalsConfig > 0) requiredApprovalsConfig else 1
    fun getMaxRangeDays(): Int = if (maxRangeDaysConfig > 0) maxRangeDaysConfig else 366
    fun getDownloadLifetimeHours(): Int = if (downloadLifetimeHoursConfig > 0) downloadLifetimeHoursConfig else 72
    fun getDefaultDownloadLimit(): Int = if (defaultDownloadLimitConfig > 0) defaultDownloadLimitConfig else 5
    fun getBuildEvery(): String = buildEveryConfig
    fun getExpireEvery(): String = expireEveryConfig
}
