package com.docuhyphen.app.api.service.subscription

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory

/**
 * Resolves how commercial plan decisions behave in this environment.
 *
 * The default enforces every decision consistently across environments. An observation
 * deployment must select `REPORT_ONLY` explicitly. An unrecognised value falls back to enforced
 * decisions rather than silently weakening the configured commercial policy.
 */
@ApplicationScoped
class SubscriptionEnforcementConfigService @Inject constructor(

    @ConfigProperty(name = "app.subscription.enforcement.mode", defaultValue = "ENFORCE")
    private val configuredMode: String,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SubscriptionEnforcementConfigService::class.java)

        val DEFAULT_MODE: SubscriptionEnforcementMode = SubscriptionEnforcementMode.ENFORCE
    }

    private val resolvedMode: SubscriptionEnforcementMode by lazy {
        SubscriptionEnforcementMode.fromCodeOrNull(configuredMode)
            ?: DEFAULT_MODE.also {
                logger.warn(
                    "Unrecognised subscription enforcement mode '{}'; falling back to {}",
                    configuredMode,
                    it,
                )
            }
    }

    fun mode(): SubscriptionEnforcementMode = resolvedMode
}

