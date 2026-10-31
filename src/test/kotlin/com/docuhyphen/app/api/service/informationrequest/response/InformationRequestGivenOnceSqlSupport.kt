package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import java.sql.Connection
import java.util.*

internal const val DOCUMENT_APPLIES = "applies"

internal fun conditionDocumentOnAnswer(
    connection: Connection,
    configured: SubmissionRuntimeSqlFixture,
    answers: FieldAnswerSqlFixture,
    hiddenDataPolicy: String,
) {
    val ruleId = UUID.randomUUID()
    execute(
        connection,
        """
        INSERT INTO information_request_template_condition_rule
            (id, template_version_id, rule_key, expression_version, hidden_data_policy)
        VALUES (?, ?, 'when-record-applies', 1, ?)
        """.trimIndent(),
        ruleId,
        configured.template.versionId,
        hiddenDataPolicy,
    )
    execute(
        connection,
        """
        INSERT INTO information_request_template_condition_predicate
            (id, condition_rule_id, template_version_id, display_order, field_definition_id, value_type, operator, text_value)
        VALUES (?, ?, ?, 0, ?, 'SHORT_TEXT', 'EQUALS', ?)
        """.trimIndent(),
        UUID.randomUUID(),
        ruleId,
        configured.template.versionId,
        answers.fieldDefinitionId,
        DOCUMENT_APPLIES,
    )
    execute(
        connection,
        "UPDATE information_request_template_requirement_binding SET requiredness = 'CONDITIONAL', conditional_rule_key = 'when-record-applies' WHERE id = ?",
        configured.documentBindingId,
    )
    execute(
        connection,
        "UPDATE information_request_template_requirement_binding SET review_policy = 'REQUIRED' WHERE id = ?",
        answers.bindingId,
    )
}

internal fun grantRespondentShares(connection: Connection, fixture: SubmissionRuntimeSqlFixture) {
    grantPartyShare(connection, fixture.requestId, fixture.contributorPartyId, fixture.contributorUserId, "CONTRIBUTOR")
    grantPartyShare(connection, fixture.requestId, fixture.attestorPartyId, fixture.attestorUserId, "ATTESTOR")
}

internal fun grantPartyShare(connection: Connection, requestId: UUID, partyId: UUID, userId: UUID, role: String) {
    val shareId = UUID.randomUUID()
    execute(
        connection,
        """
        INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
        VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', now())
        """.trimIndent(),
        shareId,
        requestId,
        userId,
        role,
    )
    execute(connection, "UPDATE information_request_party SET share_id = ? WHERE id = ?", shareId, partyId)
}

internal fun setResponseModes(connection: Connection, modes: Map<UUID, String>) {
    modes.forEach { (bindingId, mode) ->
        execute(
            connection,
            "UPDATE information_request_template_requirement_binding SET response_mode = ? WHERE id = ?",
            mode,
            bindingId
        )
    }
}
