package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.model.document.DocumentVersionLocatorKind
import jakarta.persistence.*
import java.sql.Timestamp
import java.util.*

@Entity
@Table(name = "document_version_storage_cleanup")
class DocumentVersionStorageCleanup {
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "locator_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var locatorKind: DocumentVersionLocatorKind = DocumentVersionLocatorKind.OBJECT_KEY

    @Column(name = "locator", nullable = false, length = 1024)
    lateinit var locator: String

    @Column(name = "created_at", nullable = false)
    lateinit var createdAt: Timestamp

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0

    @Column(name = "next_attempt_at", nullable = false)
    lateinit var nextAttemptAt: Timestamp
}
