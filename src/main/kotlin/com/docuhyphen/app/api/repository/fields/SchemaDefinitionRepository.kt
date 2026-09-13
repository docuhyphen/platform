package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

/** Persistence for [SchemaDefinition]. Scoped by owner like [FieldDefinitionRepository]. */
@ApplicationScoped
class SchemaDefinitionRepository :
    BaseRepository<SchemaDefinition>(SchemaDefinition::class.java)
{
    /** All schema definitions visible to an organization: its own plus PLATFORM-owned ones. */
    fun findAllForOrganization(organizationId: UUID): List<SchemaDefinition> =
        entityManager.createQuery(
            """SELECT s FROM SchemaDefinition s
               WHERE s.scopeKind = com.docuhyphen.app.api.model.entity.FieldScopeKind.PLATFORM
                  OR (s.scopeKind = com.docuhyphen.app.api.model.entity.FieldScopeKind.ORGANIZATION
                      AND s.scopeOrgId = :oid)
               ORDER BY s.displayName""",
            SchemaDefinition::class.java,
        )
            .setParameter("oid", organizationId)
            .resultList

    /** All PLATFORM-owned schema definitions. */
    fun findAllPlatform(): List<SchemaDefinition> =
        entityManager.createQuery(
            """SELECT s FROM SchemaDefinition s
               WHERE s.scopeKind = com.docuhyphen.app.api.model.entity.FieldScopeKind.PLATFORM
               ORDER BY s.displayName""",
            SchemaDefinition::class.java,
        ).resultList

    fun findByKey(
        scopeKind: FieldScopeKind,
        scopeOrgId: UUID?,
        scopeUserId: UUID?,
        namespace: String,
        schemaKey: String,
    ): SchemaDefinition? =
        entityManager.createQuery(
            """SELECT s FROM SchemaDefinition s
               WHERE s.scopeKind = :sk
                 AND ((:oid IS NULL AND s.scopeOrgId IS NULL) OR s.scopeOrgId = :oid)
                 AND ((:uid IS NULL AND s.scopeUserId IS NULL) OR s.scopeUserId = :uid)
                 AND s.namespace = :ns
                 AND s.schemaKey = :key""",
            SchemaDefinition::class.java,
        )
            .setParameter("sk", scopeKind)
            .setParameter("oid", scopeOrgId)
            .setParameter("uid", scopeUserId)
            .setParameter("ns", namespace)
            .setParameter("key", schemaKey)
            .resultList
            .firstOrNull()
}
