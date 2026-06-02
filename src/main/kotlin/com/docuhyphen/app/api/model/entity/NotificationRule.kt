package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * Scope-level routing rule: "when event X fires inside scope Y, NOTIFY / SUPPRESS
 * the assignees described by [assigneesJson]".
 *
 * - `assigneesJson` uses the same assignee DSL as
 *   [com.docuhyphen.app.api.service.workflow.AssigneeSpec], extended with an
 *   `EVENT_PAYLOAD` kind so rules can target principals carried inside the event itself.
 * - `predicateJson` is a small filter expression evaluated by `NotificationRuleEngine`
 *   (e.g. `{ "field": "subject.orgId", "equals": "<uuid>" }`). Null means the rule
 *   applies to every matching event.
 * - `priority` lets ORG rules override APP defaults when both match.
 */
@Entity
@Serializable
@Table(name = "notification_rule")
class NotificationRule
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "scope", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var scope: NotificationRuleScope = NotificationRuleScope.APP

    @Column(name = "organization_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var organizationId: UUID? = null

    @Column(name = "event_pattern", nullable = false, length = 128)
    lateinit var eventPattern: String

    @Column(name = "predicate_json", nullable = true, columnDefinition = "text")
    var predicateJson: String? = null

    @Column(name = "assignees_json", nullable = false, columnDefinition = "text")
    lateinit var assigneesJson: String

    @Column(name = "action", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var action: NotificationRuleAction = NotificationRuleAction.NOTIFY

    @Column(name = "priority", nullable = false)
    var priority: Int = 100

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

