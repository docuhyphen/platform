package com.docuhyphen.app.api.service.subscription

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory

/**
 * Resolves how commercial plan decisions behave in this environment.
 *
 * The default is deliberately non-blocking so a freshly migrated environment can be observed
 * before any request is refused. An unrecognised configured value falls back to the same
 * non-blocking default rather than failing startup or silently blocking customers.
 */
@ApplicationScoped
class SubscriptionEnforcementConfigService @Inject constructor(

    @ConfigProperty(name = "app.subscription.enforcement.mode", defaultValue = "REPORT_ONLY")
    private val configuredMode: String,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SubscriptionEnforcementConfigService::class.java)

        val DEFAULT_MODE: SubscriptionEnforcementMode = SubscriptionEnforcementMode.REPORT_ONLY
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

