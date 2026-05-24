package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "security_incident")
class SecurityIncident
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "incident_type", nullable = false)
    var incidentType: String = ""

    @Column(name = "severity", nullable = false)
    var severity: String = "MEDIUM"

    @Column(name = "actor_id")
    var actorId: UUID? = null

    @Column(name = "request_id")
    var requestId: String? = null

    @Column(name = "details", length = 2048)
    var details: String? = null

    @Column(name = "created_date", nullable = false)
    var createdDate: Timestamp = Timestamp.from(Instant.now())
}

