package com.docuhyphen.app.api.repository.organization

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.PrincipalGroupMember
import com.docuhyphen.app.api.model.entity.PrincipalKind
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class PrincipalGroupMemberRepository :
    BaseRepository<PrincipalGroupMember>(PrincipalGroupMember::class.java)
{
    fun findActiveMembers(groupId: UUID): List<PrincipalGroupMember> =
        entityManager.createQuery(
            """SELECT m FROM PrincipalGroupMember m
               WHERE m.principalGroupId = :gid AND m.isActive = true""",
            PrincipalGroupMember::class.java,
        )
            .setParameter("gid", groupId)
            .resultList

    /** Groups a given principal is an active member of (no nesting in V8). */
    fun findGroupsForPrincipal(kind: PrincipalKind, principalId: UUID): List<PrincipalGroupMember> =
        entityManager.createQuery(
            """SELECT m FROM PrincipalGroupMember m
               WHERE m.principalKind = :kind AND m.principalId = :pid AND m.isActive = true""",
            PrincipalGroupMember::class.java,
        )
            .setParameter("kind", kind)
            .setParameter("pid", principalId)
            .resultList

    fun findMembership(groupId: UUID, kind: PrincipalKind, principalId: UUID): PrincipalGroupMember? =
        entityManager.createQuery(
            """SELECT m FROM PrincipalGroupMember m
               WHERE m.principalGroupId = :gid
                 AND m.principalKind = :kind
                 AND m.principalId = :pid""",
            PrincipalGroupMember::class.java,
        )
            .setParameter("gid", groupId)
            .setParameter("kind", kind)
            .setParameter("pid", principalId)
            .resultList
            .firstOrNull()
}

