package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import org.slf4j.LoggerFactory

/**
 * Records that a caller reached a superseded Fields write surface, and whether that caller stated
 * the version it was changing.
 *
 * The superseded surface stays behaviorally identical for clients that have not migrated, which
 * means nothing about it fails loudly enough to be noticed. Deciding when it can start requiring a
 * stated version, or be withdrawn, needs the two counts this records: how many callers still reach
 * it at all, and how many of those still change data without saying what they read. Both are
 * emitted under one stable event name so the window can be measured from the service log without a
 * schema change or a new counter store.
 */
@ApplicationScoped
class DeprecatedFieldsWriteUsage
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(DeprecatedFieldsWriteUsage::class.java)
    }

    /**
     * One call to the superseded surface. [conditioned] is false when the caller stated no version,
     * which is the usage that has to reach zero before the surface can require one.
     */
    fun record(resource: FieldsResourceRef, principal: PrincipalRef, conditioned: Boolean)
    {
        logger.info(
            "Deprecated Fields write surface used resourceType={} resourceId={} " +
                "principalKind={} principalId={} conditioned={}",
            resource.resourceType, resource.resourceId, principal.kind, principal.id, conditioned,
        )
    }
}
