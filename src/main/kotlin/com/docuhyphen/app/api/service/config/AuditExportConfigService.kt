package com.docuhyphen.app.api.service.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

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

    @ConfigProperty(name = "app.audit.export.build-lease-minutes", defaultValue = "10")
    private val buildLeaseMinutesConfig: Int,
)
{
    fun isDualControlRequired(): Boolean = dualControlRequiredConfig
    fun getRequiredApprovals(): Int = if (requiredApprovalsConfig > 0) requiredApprovalsConfig else 1
    fun getMaxRangeDays(): Int = if (maxRangeDaysConfig > 0) maxRangeDaysConfig else 366
    fun getDownloadLifetimeHours(): Int = if (downloadLifetimeHoursConfig > 0) downloadLifetimeHoursConfig else 72
    fun getDefaultDownloadLimit(): Int = if (defaultDownloadLimitConfig > 0) defaultDownloadLimitConfig else 5
    fun getBuildEvery(): String = buildEveryConfig
    fun getExpireEvery(): String = expireEveryConfig

    fun getBuildLeaseMinutes(): Int = if (buildLeaseMinutesConfig > 0) buildLeaseMinutesConfig else 10
}
