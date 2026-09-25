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
 * Current draft response for one runtime Requirement occurrence.
 *
 * Submission packages later copy the exact response revision they include. Until then this row is
 * the mutable draft, guarded by the parent request's response revision and recorded with canonical
 * principal provenance.
 */
@Entity
@Table(name = "information_request_response")
class InformationRequestResponse
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "information_request_requirement_id", nullable = false)
    lateinit var informationRequestRequirementId: UUID

    @Column(name = "requirement_revision_id", nullable = false)
    lateinit var requirementRevisionId: UUID

    @Column(name = "occurrence_path", nullable = false, length = 512)
    lateinit var occurrencePath: String

    @Column(name = "disposition", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var disposition: InformationRequestResponseDisposition = InformationRequestResponseDisposition.NOT_ANSWERED

    @Column(name = "narrative", columnDefinition = "text")
    var narrative: String? = null

    @Column(name = "field_value_set_id")
    var fieldValueSetId: UUID? = null

    @Column(name = "active_in_response", nullable = false)
    var activeInResponse: Boolean = true

    @Column(name = "hidden_by_condition_rule_key", length = 128)
    var hiddenByConditionRuleKey: String? = null

    @Column(name = "hidden_data_policy", length = 48)
    @Enumerated(EnumType.STRING)
    var hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy? = null

    @Column(name = "hidden_at")
    var hiddenAt: Timestamp? = null

    @Column(name = "reconfirmation_required_by_amendment_id")
    var reconfirmationRequiredByAmendmentId: UUID? = null

    @Column(name = "response_revision", nullable = false)
    var responseRevision: Long = 1

    @Column(name = "recorded_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var recordedByPrincipalKind: PrincipalKind

    @Column(name = "recorded_by_principal_id", nullable = false)
    lateinit var recordedByPrincipalId: UUID

    @Column(name = "recorded_by_session_ref", length = 64)
    var recordedBySessionRef: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
