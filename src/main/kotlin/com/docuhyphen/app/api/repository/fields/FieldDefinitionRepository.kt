package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [FieldDefinition]. Lookups are always scoped by owner (PLATFORM or an
 * organization) so stable keys never collide across scopes.
 */
@ApplicationScoped
class FieldDefinitionRepository :
    BaseRepository<FieldDefinition>(FieldDefinition::class.java)
{
    /** All field definitions visible to an organization: its own plus PLATFORM-owned ones. */
    fun findAllForOrganization(organizationId: UUID): List<FieldDefinition> =
        entityManager.createQuery(
            """SELECT f FROM FieldDefinition f
               WHERE f.scopeKind = com.docuhyphen.app.api.model.entity.FieldScopeKind.PLATFORM
                  OR (f.scopeKind = com.docuhyphen.app.api.model.entity.FieldScopeKind.ORGANIZATION
                      AND f.scopeOrgId = :oid)
               ORDER BY f.namespace, f.fieldKey""",
            FieldDefinition::class.java,
        )
            .setParameter("oid", organizationId)
            .resultList

    /** All PLATFORM-owned field definitions. */
    fun findAllPlatform(): List<FieldDefinition> =
        entityManager.createQuery(
            """SELECT f FROM FieldDefinition f
               WHERE f.scopeKind = com.docuhyphen.app.api.model.entity.FieldScopeKind.PLATFORM
               ORDER BY f.namespace, f.fieldKey""",
            FieldDefinition::class.java,
        ).resultList

    /** Finds a definition by its stable key within an owner scope, or null. */
    fun findByKey(
        scopeKind: FieldScopeKind,
        scopeOrgId: UUID?,
        namespace: String,
        fieldKey: String,
    ): FieldDefinition? =
        entityManager.createQuery(
            """SELECT f FROM FieldDefinition f
               WHERE f.scopeKind = :sk
                 AND ((:oid IS NULL AND f.scopeOrgId IS NULL) OR f.scopeOrgId = :oid)
                 AND f.namespace = :ns
                 AND f.fieldKey = :fk""",
            FieldDefinition::class.java,
        )
            .setParameter("sk", scopeKind)
            .setParameter("oid", scopeOrgId)
            .setParameter("ns", namespace)
            .setParameter("fk", fieldKey)
            .resultList
            .firstOrNull()
}
