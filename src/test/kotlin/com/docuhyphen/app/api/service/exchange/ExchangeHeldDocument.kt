package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.model.entity.Exchange
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.persistence.EntityManager
import java.sql.Timestamp
import java.time.Instant
import java.util.*

internal object ExchangeHeldDocument {
    fun stored(entityManager: EntityManager): UUID {
        val ownerId = UUID.randomUUID()
        val document = Document().apply {
            title = "Process record"
            type = DocumentType.PDF
            createdDate = Timestamp.from(Instant.now())
            updateDate = Timestamp.from(Instant.now())
        }
        QuarkusTransaction.requiringNew().run {
            entityManager.createNativeQuery(
                """
                INSERT INTO app_user
                    (id, is_active, created_date, email, email_verification_completed, is_temporary,
                     sign_in_attempts, exchange_version, multifactor_authentication_type,
                     is_password_temporary, email_mfa_fallback_enabled)
                VALUES (?1, TRUE, ?2, ?3, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(),
            )
                .setParameter(1, ownerId)
                .setParameter(2, Timestamp.from(Instant.now()))
                .setParameter(3, "record-owner-${ownerId.toString().take(8)}@process.test")
                .executeUpdate()
            entityManager.persist(
                Exchange().apply {
                    name = "Process exchange"
                    description = "Holds the process record"
                    initialShareMessage = ""
                    ownerUserId = ownerId
                    isDeleted = false
                    dateDeleted = null
                    documents = mutableListOf(document)
                },
            )
        }
        return document.id
    }
}
