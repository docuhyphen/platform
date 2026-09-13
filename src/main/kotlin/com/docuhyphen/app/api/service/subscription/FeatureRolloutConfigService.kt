package com.docuhyphen.app.api.service.subscription

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import java.util.Optional
import java.util.UUID

/**
 * Resolves which owners a capability under controlled release has been turned on for in this
 * deployment.
 *
 * This is an operational readiness decision and is deliberately separate from the commercial
 * decision recorded against an owner. A commercial grant says the owner is entitled to the
 * capability; a rollout grant says this deployment is ready to serve it to that owner. They are
 * made by different people at different times, so they are stated in different places and neither
 * one implies the other.
 *
 * Grants are deployment configuration rather than stored rows, so they never travel with the data
 * and an environment that was never configured refuses everything. Each entry names one capability,
 * one owner kind, and one owner:
 *
 * ```
 * app.subscription.rollout.grants=INFORMATION_REQUESTS:ORGANIZATION:<id>,INFORMATION_REQUESTS:USER:<id>
 * ```
 *
 * An entry that cannot be read is ignored with a warning rather than widened, so a typo leaves the
 * capability closed instead of opening it for somebody unintended.
 */
@ApplicationScoped
class FeatureRolloutConfigService @Inject constructor(

    // Optional<String> because a deployment that has released the capability to nobody leaves this
    // empty, and SmallRye Config converts an empty value to null, which it then refuses to inject
    // into a plain String parameter.
    @ConfigProperty(name = "app.subscription.rollout.grants")
    private val configuredGrants: Optional<String>,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(FeatureRolloutConfigService::class.java)

        private const val ENTRY_SEPARATOR = ','
        private const val FIELD_SEPARATOR = ':'
        private const val FIELDS_PER_ENTRY = 3
    }

    private val grantsByOwner: Map<SubscriptionContext, Set<PlanFeature>> by lazy {
        readGrants(configuredGrants.orElse(""))
    }

    /** Whether this deployment has turned the capability on for exactly this owner. */
    fun isGranted(context: SubscriptionContext, feature: PlanFeature): Boolean
    {
        return grantedFeatures(context).contains(feature)
    }

    /** Every capability this deployment has turned on for the owner, empty when none has been. */
    fun grantedFeatures(context: SubscriptionContext): Set<PlanFeature>
    {
        return grantsByOwner[context] ?: emptySet()
    }

    private fun readGrants(configured: String): Map<SubscriptionContext, Set<PlanFeature>>
    {
        val grants = mutableMapOf<SubscriptionContext, MutableSet<PlanFeature>>()

        configured.split(ENTRY_SEPARATOR)
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .forEach { entry ->
                val grant = readEntry(entry)
                if (grant == null)
                {
                    logger.warn(
                        "Ignoring unreadable feature rollout grant '{}'; expected the form " +
                            "FEATURE{}OWNER_TYPE{}OWNER_ID",
                        entry,
                        FIELD_SEPARATOR,
                        FIELD_SEPARATOR,
                    )
                    return@forEach
                }

                grants.getOrPut(grant.owner) { mutableSetOf() }.add(grant.feature)
            }

        if (grants.isNotEmpty())
        {
            logger.info(
                "event=feature_rollout_configured owners={} grants={}",
                grants.size,
                grants.values.sumOf { it.size },
            )
        }

        return grants
    }

    private fun readEntry(entry: String): OwnerFeatureGrant?
    {
        val fields = entry.split(FIELD_SEPARATOR)
        if (fields.size != FIELDS_PER_ENTRY)
        {
            return null
        }

        val feature = PlanFeature.fromCodeOrNull(fields[0]) ?: return null
        val ownerType = SubscriptionOwnerType.fromCodeOrNull(fields[1]) ?: return null
        val ownerId = readOwnerId(fields[2]) ?: return null

        return OwnerFeatureGrant(SubscriptionContext(ownerType, ownerId), feature)
    }

    private fun readOwnerId(value: String): UUID?
    {
        return try
        {
            UUID.fromString(value.trim())
        }
        catch (exception: IllegalArgumentException)
        {
            null
        }
    }

    /** One readable entry: the owner it names and the capability it opens for them. */
    private data class OwnerFeatureGrant(
        val owner: SubscriptionContext,
        val feature: PlanFeature,
    )
}
