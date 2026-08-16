package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.RefreshTokenRecord
import com.docuhyphen.app.api.repository.auth.RefreshTokenRecordRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@RequestScoped
class RefreshTokenRecordService @Inject constructor(
    private val refreshTokenRecordRepository: RefreshTokenRecordRepository,
)
{
    fun recordIssued(
        userId: UUID,
        userSessionId: UUID?,
        familyId: String,
        jti: String,
        tokenHash: String,
        expiresAt: Instant,
    )
    {
        val existing = refreshTokenRecordRepository.findByJti(jti)
        if (existing != null)
        {
            return
        }

        refreshTokenRecordRepository.save(
            RefreshTokenRecord().apply {
                this.userId = userId
                this.userSessionId = userSessionId
                this.familyId = familyId
                this.jti = jti
                this.tokenHash = tokenHash
                this.status = "ACTIVE"
                this.issuedAt = Timestamp.from(Instant.now())
                this.expiresAt = Timestamp.from(expiresAt)
            }
        )
    }

    fun recordRotation(currentJti: String, successorJti: String, graceSeconds: Long)
    {
        val now = Instant.now()
        refreshTokenRecordRepository.markConsumed(
            jti = currentJti,
            consumedAt = Timestamp.from(now),
            graceUntil = Timestamp.from(now.plusSeconds(graceSeconds)),
            successorJti = successorJti,
        )
    }

    fun revokeFamily(familyId: String, reasonCode: RevocationReasonCode)
    {
        refreshTokenRecordRepository.revokeByFamily(
            familyId = familyId,
            revokedAt = Timestamp.from(Instant.now()),
            reason = reasonCode.name,
        )
    }

    fun revokeUser(userId: UUID, reasonCode: RevocationReasonCode)
    {
        refreshTokenRecordRepository.revokeByUser(
            userId = userId,
            revokedAt = Timestamp.from(Instant.now()),
            reason = reasonCode.name,
        )
    }

    fun revokeByJti(jti: String, reasonCode: RevocationReasonCode)
    {
        refreshTokenRecordRepository.revokeByJti(
            jti = jti,
            revokedAt = Timestamp.from(Instant.now()),
            reason = reasonCode.name,
        )
    }
}

