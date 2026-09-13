package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Records the audit trail for choosing a Schema for a resource, removing it, and changing the values
 * held against it. Assembling the drafts here keeps the assignment service free of audit plumbing and
 * keeps the payload rules for these events in one place.
 *
 * A payload never carries an answer. Which questions were changed and how many is enough to
 * reconstruct what happened, while the answers themselves belong to the Fields tables and their
 * visibility rules; a value copied into an audit payload would escape those rules entirely.
 */
@ApplicationScoped
class SchemaAssignmentAuditTrail @Inject constructor(
    private val auditRecorder: AuditRecorder,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SchemaAssignmentAuditTrail::class.java)
        private const val TARGET_TYPE = "SCHEMA_ASSIGNMENT"
    }

    fun schemaAssigned(
        assignment: SchemaAssignment,
        ownerScope: ScopeReference?,
        provenance: FieldPrincipalProvenance,
        schemaDisplayName: String?,
    ) = record(
        AuditEventType.SCHEMA_ASSIGNMENT_ASSIGN, assignment, ownerScope, provenance, schemaDisplayName,
        payload = emptyMap(),
    )

    fun schemaUnassigned(
        assignment: SchemaAssignment,
        ownerScope: ScopeReference?,
        provenance: FieldPrincipalProvenance,
        schemaDisplayName: String?,
        removedValueCount: Int,
    ) = record(
        AuditEventType.SCHEMA_ASSIGNMENT_UNASSIGN, assignment, ownerScope, provenance, schemaDisplayName,
        payload = mapOf("removedValueCount" to removedValueCount.toString()),
    )

    /**
     * @param changedCount questions whose stored answer this call actually changed.
     * @param clearedCount how many of those were emptied rather than given a value.
     */
    fun valuesUpdated(
        assignment: SchemaAssignment,
        ownerScope: ScopeReference?,
        provenance: FieldPrincipalProvenance,
        schemaDisplayName: String?,
        changedCount: Int,
        clearedCount: Int,
    ) = record(
        AuditEventType.FIELD_VALUE_UPDATE, assignment, ownerScope, provenance, schemaDisplayName,
        payload = mapOf(
            "changedCount" to changedCount.toString(),
            "clearedCount" to clearedCount.toString(),
        ),
    )

    private fun record(
        eventType: AuditEventType,
        assignment: SchemaAssignment,
        ownerScope: ScopeReference?,
        provenance: FieldPrincipalProvenance,
        schemaDisplayName: String?,
        payload: Map<String, String>,
    )
    {
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    owner = ownerOf(ownerScope),
                    eventTypeKey = eventType.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = provenance.principal.id,
                    actorKind = AuditActorKind.forPrincipal(provenance.principal.kind),
                    targetType = TARGET_TYPE,
                    targetId = assignment.id.toString(),
                    targetLabel = schemaDisplayName,
                    sessionId = provenance.sessionRef,
                    payload = payload + mapOf(
                        "resourceType" to assignment.resourceType,
                        "resourceId" to assignment.resourceId.toString(),
                        "schemaVersionId" to assignment.schemaVersionId.toString(),
                        "actorPrincipalKind" to provenance.principal.kind.name,
                    ),
                ),
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("SchemaAssignmentAuditTrail: AuditRecorder rejected {} draft: {}", eventType.key, e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error(
                "SchemaAssignmentAuditTrail: AuditRecorder capture failed for {}: {}",
                eventType.key, e.message, e,
            )
        }
    }

    /** The owning scope the audit record is filed under. */
    private fun ownerOf(ownerScope: ScopeReference?): AuditOwnerScope = when (ownerScope)
    {
        is ScopeReference.Organization -> AuditOwnerScope.Organization(ownerScope.organizationId)
        is ScopeReference.Personal -> AuditOwnerScope.Personal(ownerScope.userId)
        ScopeReference.Platform, null -> AuditOwnerScope.Platform
    }
}
