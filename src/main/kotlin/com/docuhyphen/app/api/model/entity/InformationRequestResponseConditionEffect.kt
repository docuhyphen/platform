package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class InformationRequestConditionEffectKind {
    HIDDEN,
    CLEARED,
    ARCHIVED,
    REACTIVATED,
}

@Entity
@Table(name = "information_request_response_condition_effect")
class InformationRequestResponseConditionEffect {
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "information_request_requirement_id", nullable = false)
    lateinit var informationRequestRequirementId: UUID

    @Column(name = "response_id", nullable = false)
    lateinit var responseId: UUID

    @Column(name = "transition_id", nullable = false)
    lateinit var transitionId: UUID

    @Column(name = "occurrence_path", nullable = false, length = 512)
    lateinit var occurrencePath: String

    @Column(name = "rule_key", nullable = false, length = 128)
    lateinit var ruleKey: String

    @Column(name = "expression_version", nullable = false)
    var expressionVersion: Int = 1

    @Column(name = "evaluation_state", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    lateinit var evaluationState: InformationRequestConditionEvaluationState

    @Column(name = "hidden_data_policy", nullable = false, length = 48)
    @Enumerated(EnumType.STRING)
    lateinit var hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy

    @Column(name = "effect", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var effect: InformationRequestConditionEffectKind

    @Column(name = "archive_id")
    var archiveId: UUID? = null

    @Column(name = "response_revision", nullable = false)
    var responseRevision: Long = 1

    @Column(name = "recorded_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var recordedByPrincipalKind: PrincipalKind

    @Column(name = "recorded_by_principal_id", nullable = false)
    lateinit var recordedByPrincipalId: UUID

    @Column(name = "recorded_by_session_ref", length = 64)
    var recordedBySessionRef: String? = null

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
