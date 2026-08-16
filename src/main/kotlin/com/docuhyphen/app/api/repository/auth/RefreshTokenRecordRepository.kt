package com.docuhyphen.app.api.repository.auth

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.RefreshTokenRecord
import jakarta.enterprise.context.RequestScoped
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.util.UUID

@RequestScoped
class RefreshTokenRecordRepository : BaseRepository<RefreshTokenRecord>(RefreshTokenRecord::class.java)
{
    fun findByJti(jti: String): RefreshTokenRecord?
    {
        return entityManager.createQuery(
            "SELECT r FROM RefreshTokenRecord r WHERE r.jti = :jti",
            RefreshTokenRecord::class.java,
        )
            .setParameter("jti", jti)
            .resultList
            .firstOrNull()
    }

    @Transactional
    fun markConsumed(jti: String, consumedAt: Timestamp, graceUntil: Timestamp, successorJti: String)
    {
        entityManager.createQuery(
            "UPDATE RefreshTokenRecord r SET r.status = 'CONSUMED', r.consumedAt = :consumedAt, r.graceUntil = :graceUntil WHERE r.jti = :jti",
        )
            .setParameter("consumedAt", consumedAt)
            .setParameter("graceUntil", graceUntil)
            .setParameter("jti", jti)
            .executeUpdate()

        entityManager.createQuery(
            "UPDATE RefreshTokenRecord r SET r.rotatedFromJti = :rotatedFrom WHERE r.jti = :successorJti",
        )
            .setParameter("rotatedFrom", jti)
            .setParameter("successorJti", successorJti)
            .executeUpdate()
    }

    @Transactional
    fun revokeByFamily(familyId: String, revokedAt: Timestamp, reason: String)
    {
        entityManager.createQuery(
            "UPDATE RefreshTokenRecord r SET r.status = 'REVOKED', r.revokedAt = :revokedAt, r.revocationReasonCode = :reason WHERE r.familyId = :familyId AND r.status <> 'REVOKED'",
        )
            .setParameter("revokedAt", revokedAt)
            .setParameter("reason", reason)
            .setParameter("familyId", familyId)
            .executeUpdate()
    }

    @Transactional
    fun revokeByUser(userId: UUID, revokedAt: Timestamp, reason: String)
    {
        entityManager.createQuery(
            "UPDATE RefreshTokenRecord r SET r.status = 'REVOKED', r.revokedAt = :revokedAt, r.revocationReasonCode = :reason WHERE r.userId = :userId AND r.status <> 'REVOKED'",
        )
            .setParameter("revokedAt", revokedAt)
            .setParameter("reason", reason)
            .setParameter("userId", userId)
            .executeUpdate()
    }

    @Transactional
    fun revokeByJti(jti: String, revokedAt: Timestamp, reason: String)
    {
        entityManager.createQuery(
            "UPDATE RefreshTokenRecord r SET r.status = 'REVOKED', r.revokedAt = :revokedAt, r.revocationReasonCode = :reason WHERE r.jti = :jti",
        )
            .setParameter("revokedAt", revokedAt)
            .setParameter("reason", reason)
            .setParameter("jti", jti)
            .executeUpdate()
    }
}

