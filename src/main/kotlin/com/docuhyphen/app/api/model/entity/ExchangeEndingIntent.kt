package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "exchange_ending_intent")
class ExchangeEndingIntent {
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "exchange_id", nullable = false)
    lateinit var exchangeId: UUID

    @Column(name = "workflow_instance_id", nullable = false)
    lateinit var workflowInstanceId: UUID

    @Column(name = "cancel_remaining_information_requests", nullable = false)
    var cancelRemainingInformationRequests: Boolean = false

    @Column(name = "requested_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var requestedByPrincipalKind: PrincipalKind

    @Column(name = "requested_by_principal_id", nullable = false)
    lateinit var requestedByPrincipalId: UUID

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())
}
