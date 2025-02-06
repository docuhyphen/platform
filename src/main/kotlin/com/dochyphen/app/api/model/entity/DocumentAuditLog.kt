// DocumentAuditLog.kt
package com.dochyphen.app.api.model.entity

import com.dochyphen.app.api.serializer.TimestampSerializer
import com.dochyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

enum class DocumentAuditLogAction {
    UPLOAD,
    DOWNLOAD,
    VIEW,
    CREATED,
    DELETE,
    UPDATE,
}

@Entity
@Table(name = "audit_log")
@Serializable
class DocumentAuditLog {

    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @ManyToOne
    @JoinColumn(name = "document_id", nullable = false)
    var document: Document = Document()

    @Column(name = "action", nullable = false)
    @Enumerated(EnumType.STRING)
    var action: DocumentAuditLogAction = DocumentAuditLogAction.UPLOAD

    @ManyToOne(cascade = [CascadeType.PERSIST], fetch = FetchType.EAGER)
    @JoinColumn(name = "performed_by_app_user_id", unique = false)
    var performedBy: AppUser? = null

    @Column(name = "performed_email", nullable = false)
    var performedEmail: String? = null

    @Column(name = "timestamp", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var timestamp: Timestamp = Timestamp.from(Instant.now())

    constructor()
}