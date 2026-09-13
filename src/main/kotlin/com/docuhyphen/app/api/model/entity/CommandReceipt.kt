package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Durable record that one command already completed inside its idempotency scope.
 *
 * A caller may retry the same command after a timeout or lost response. The receipt lets the service
 * replay the stored result reference when the retry names the same request fingerprint, while
 * refusing a reused key that now names a different request.
 */
@Entity
@Table(name = "command_receipt")
class CommandReceipt
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "resource_type", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    lateinit var resourceType: ResourceType

    @Column(name = "resource_id", nullable = false)
    lateinit var resourceId: UUID

    @Column(name = "operation_name", nullable = false, length = 96)
    lateinit var operationName: String

    @Column(name = "actor_kind", nullable = false, length = 32)
    lateinit var actorKind: String

    @Column(name = "actor_id", nullable = false)
    lateinit var actorId: UUID

    @Column(name = "idempotency_key", nullable = false, length = 256)
    lateinit var idempotencyKey: String

    @Column(name = "request_fingerprint_sha256", nullable = false, length = 128)
    lateinit var requestFingerprintSha256: String

    @Column(name = "result_resource_type", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    lateinit var resultResourceType: ResourceType

    @Column(name = "result_resource_id", nullable = false)
    lateinit var resultResourceId: UUID

    @Column(name = "result_revision")
    var resultRevision: Long? = null

    @Column(name = "result_etag", length = 128)
    var resultETag: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "completed_at", nullable = false)
    var completedAt: Timestamp = Timestamp.from(Instant.now())
}
