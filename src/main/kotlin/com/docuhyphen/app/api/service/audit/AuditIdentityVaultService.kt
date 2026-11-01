package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.model.entity.AuditIdentityVaultKey
import com.docuhyphen.app.api.repository.audit.AuditIdentityVaultKeyRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.audit.identity.AuditIdentityVaultMasterKeyProvider
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@ApplicationScoped
class AuditIdentityVaultService @Inject constructor(
    private val auditIdentityVaultKeyRepository: AuditIdentityVaultKeyRepository,
    private val masterKeyProvider: AuditIdentityVaultMasterKeyProvider,
    private val auditRecorder: AuditRecorder,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditIdentityVaultService::class.java)
        private const val AES_ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_SIZE_BYTES = 12
        private const val DATA_KEY_SIZE_BYTES = 32
    }

    @Transactional
    fun ensureKey(subjectType: String, subjectId: String): UUID
    {
        auditIdentityVaultKeyRepository.findBySubject(subjectType, subjectId)?.let { return it.id }

        val dataKey = ByteArray(DATA_KEY_SIZE_BYTES)
        SecureRandom().nextBytes(dataKey)
        val iv = ByteArray(IV_SIZE_BYTES)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(masterKeyProvider.keyBytes(), "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        val wrapped = cipher.doFinal(dataKey)

        val entry = AuditIdentityVaultKey().apply {
            this.subjectType = subjectType
            this.subjectId = subjectId
            this.wrappedKey = Base64.getEncoder().encodeToString(wrapped)
            this.iv = Base64.getEncoder().encodeToString(iv)
            this.createdAt = Timestamp.from(Instant.now())
        }
        return auditIdentityVaultKeyRepository.save(entry).id
    }

    fun unwrapDataKey(subjectType: String, subjectId: String): ByteArray?
    {
        val entry = auditIdentityVaultKeyRepository.findBySubject(subjectType, subjectId) ?: return null
        val wrapped = entry.wrappedKey ?: return null
        val iv = entry.iv ?: return null

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(masterKeyProvider.keyBytes(), "AES"),
            GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(iv)),
        )
        return cipher.doFinal(Base64.getDecoder().decode(wrapped))
    }

    fun isShredded(subjectType: String, subjectId: String): Boolean =
        auditIdentityVaultKeyRepository.findBySubject(subjectType, subjectId)?.shreddedAt != null

    @Transactional
    fun shred(subjectType: String, subjectId: String, shreddedByUserId: UUID?): Boolean
    {
        val entry = auditIdentityVaultKeyRepository.findBySubject(subjectType, subjectId) ?: return false
        if (entry.shreddedAt != null)
        {
            return true
        }

        entry.wrappedKey = null
        entry.iv = null
        entry.shreddedAt = Timestamp.from(Instant.now())
        auditIdentityVaultKeyRepository.update(entry)

        recordShredEvent(subjectType, subjectId, shreddedByUserId)
        return true
    }

    private fun recordShredEvent(subjectType: String, subjectId: String, actorId: UUID?)
    {
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    owner = AuditOwnerScope.Platform,
                    eventTypeKey = AuditEventType.AUDIT_IDENTITY_KEY_SHREDDED.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = actorId,
                    actorKind = if (actorId == null) AuditActorKind.SYSTEM else AuditActorKind.HUMAN,
                    actorRole = if (actorId == null) "SYSTEM" else "AUDIT_GOVERNANCE",
                    targetType = "AUDIT_IDENTITY_VAULT_KEY",
                    targetId = "$subjectType:$subjectId",
                    payload = mapOf("subject_type" to subjectType),
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("AuditIdentityVaultService: AuditRecorder rejected shred event: {}", e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("AuditIdentityVaultService: AuditRecorder capture failed for shred event: {}", e.message, e)
        }
    }
}
