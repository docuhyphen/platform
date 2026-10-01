package com.docuhyphen.app.api.repository.auth

import com.docuhyphen.app.api.model.entity.ServiceAccount
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class ServiceAccountRepository : BaseRepository<ServiceAccount>(ServiceAccount::class.java)
{
    fun findActiveByOrg(organizationId: UUID): List<ServiceAccount> =
        entityManager.createQuery(
            """SELECT s FROM ServiceAccount s
               WHERE s.organizationId = :oid AND s.isActive = true""",
            ServiceAccount::class.java,
        )
            .setParameter("oid", organizationId)
            .resultList
}

