package com.docuhyphen.app.api.repository.documentlibrary

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.model.entity.DocumentLibraryEntry
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class DocumentLibraryRepository :
    BaseRepository<DocumentLibraryEntry>(DocumentLibraryEntry::class.java)
{
    fun findAllAccessibleForCaller(
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
    ): List<DocumentLibraryEntry>
    {
        return if (callerOrgId != null)
        {
            entityManager.createQuery(
                """SELECT d FROM DocumentLibraryEntry d
                   WHERE d.isDeleted = false
                     AND (
                           (d.scope = com.docuhyphen.app.api.model.entity.BlueprintScope.PERSONAL
                            AND d.createdByAppUserId = :uid)
                        OR (d.scope = com.docuhyphen.app.api.model.entity.BlueprintScope.ORG
                            AND d.organizationId = :oid
                            AND (:showUnpublished = true OR d.isPublished = true))
                        OR (d.scope = com.docuhyphen.app.api.model.entity.BlueprintScope.APP
                            AND d.isPublished = true)
                     )
                   ORDER BY d.updatedAt DESC""",
                DocumentLibraryEntry::class.java,
            )
                .setParameter("uid", callerUserId)
                .setParameter("oid", callerOrgId)
                .setParameter("showUnpublished", isOrgAdmin)
                .resultList
        }
        else
        {
            entityManager.createQuery(
                """SELECT d FROM DocumentLibraryEntry d
                   WHERE d.isDeleted = false
                     AND (
                           (d.scope = com.docuhyphen.app.api.model.entity.BlueprintScope.PERSONAL
                            AND d.createdByAppUserId = :uid)
                        OR (d.scope = com.docuhyphen.app.api.model.entity.BlueprintScope.APP
                            AND d.isPublished = true)
                     )
                   ORDER BY d.updatedAt DESC""",
                DocumentLibraryEntry::class.java,
            )
                .setParameter("uid", callerUserId)
                .resultList
        }
    }
}
