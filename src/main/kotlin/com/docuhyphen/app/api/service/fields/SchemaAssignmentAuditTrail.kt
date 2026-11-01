package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.entity.FieldValueProvenance
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.model.fields.FieldPrincipalProvenance
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

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
        defaultValueCount: Int,
    ) = record(
        AuditEventType.SCHEMA_ASSIGNMENT_ASSIGN, assignment, ownerScope, provenance, schemaDisplayName,
        payload = mapOf("defaultValueCount" to defaultValueCount.toString()),
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

    fun valuesUpdated(
        assignment: SchemaAssignment,
        ownerScope: ScopeReference?,
        provenance: FieldPrincipalProvenance,
        schemaDisplayName: String?,
        valueProvenance: FieldValueProvenance,
        changedCount: Int,
        clearedCount: Int,
        fieldKeys: List<String>,
    ) = record(
        AuditEventType.FIELD_VALUE_UPDATE, assignment, ownerScope, provenance, schemaDisplayName,
        payload = mapOf(
            "valueProvenance" to valueProvenance.name,
            "changedCount" to changedCount.toString(),
            "clearedCount" to clearedCount.toString(),
            "fieldKeys" to fieldKeys.joinToString(","),
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
    }

    private fun ownerOf(ownerScope: ScopeReference?): AuditOwnerScope = when (ownerScope)
    {
        is ScopeReference.Organization -> AuditOwnerScope.Organization(ownerScope.organizationId)
        is ScopeReference.Personal -> AuditOwnerScope.Personal(ownerScope.userId)
        ScopeReference.Platform, null -> AuditOwnerScope.Platform
    }
}
