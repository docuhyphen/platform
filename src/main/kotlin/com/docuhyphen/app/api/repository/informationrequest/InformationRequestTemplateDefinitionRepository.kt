package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateDefinition]. Every lookup names the owner it is asking
 * about, because a key belongs to one owner and answering for a scope kind alone would answer for
 * every owner of that kind at once.
 */
@ApplicationScoped
class InformationRequestTemplateDefinitionRepository :
    BaseRepository<InformationRequestTemplateDefinition>(InformationRequestTemplateDefinition::class.java)
{
    fun findByKey(
        scopeKind: InformationRequestTemplateScopeKind,
        scopeOrgId: UUID?,
        scopeUserId: UUID?,
        namespace: String,
        templateKey: String,
    ): InformationRequestTemplateDefinition? =
        entityManager.createQuery(
            """
            SELECT definition
            FROM InformationRequestTemplateDefinition definition
            WHERE definition.scopeKind = :scopeKind
              AND ((:orgId IS NULL AND definition.scopeOrgId IS NULL) OR definition.scopeOrgId = :orgId)
              AND ((:userId IS NULL AND definition.scopeUserId IS NULL) OR definition.scopeUserId = :userId)
              AND definition.namespace = :namespace
              AND definition.templateKey = :templateKey
            """.trimIndent(),
            InformationRequestTemplateDefinition::class.java,
        )
            .setParameter("scopeKind", scopeKind)
            .setParameter("orgId", scopeOrgId)
            .setParameter("userId", scopeUserId)
            .setParameter("namespace", namespace)
            .setParameter("templateKey", templateKey)
            .resultList
            .firstOrNull()

    fun findAllPlatform(): List<InformationRequestTemplateDefinition> =
        entityManager.createQuery(
            """
            SELECT definition
            FROM InformationRequestTemplateDefinition definition
            WHERE definition.scopeKind = com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind.PLATFORM
              AND definition.originKind =
                  com.docuhyphen.app.api.model.entity.InformationRequestTemplateOriginKind.REUSABLE
            ORDER BY definition.displayName
            """.trimIndent(),
            InformationRequestTemplateDefinition::class.java,
        ).resultList

    /** An organization's own definitions plus the platform-owned ones it may also use. */
    fun findAllForOrganization(organizationId: UUID): List<InformationRequestTemplateDefinition> =
        entityManager.createQuery(
            """
            SELECT definition
            FROM InformationRequestTemplateDefinition definition
            WHERE definition.originKind =
                  com.docuhyphen.app.api.model.entity.InformationRequestTemplateOriginKind.REUSABLE
              AND (definition.scopeKind = com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind.PLATFORM
               OR (definition.scopeKind = com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind.ORGANIZATION
                   AND definition.scopeOrgId = :orgId))
            ORDER BY definition.displayName
            """.trimIndent(),
            InformationRequestTemplateDefinition::class.java,
        )
            .setParameter("orgId", organizationId)
            .resultList

    /** Only the definitions this one person owns, never another person's and never the platform's. */
    fun findAllForUser(userId: UUID): List<InformationRequestTemplateDefinition> =
        entityManager.createQuery(
            """
            SELECT definition
            FROM InformationRequestTemplateDefinition definition
            WHERE definition.scopeKind = com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind.PERSONAL
              AND definition.scopeUserId = :userId
              AND definition.originKind =
                  com.docuhyphen.app.api.model.entity.InformationRequestTemplateOriginKind.REUSABLE
            ORDER BY definition.displayName
            """.trimIndent(),
            InformationRequestTemplateDefinition::class.java,
        )
            .setParameter("userId", userId)
            .resultList
}
