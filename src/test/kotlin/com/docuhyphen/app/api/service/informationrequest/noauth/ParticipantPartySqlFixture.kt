package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import java.sql.Connection
import java.util.*

internal fun contributorAsParticipant(
    connection: Connection,
    fixture: SubmissionRuntimeSqlFixture,
    email: String = "participant-${UUID.randomUUID().toString().take(8)}@process.test",
): UUID {
    val participantId = UUID.randomUUID()
    execute(
        connection, """
        INSERT INTO external_participant (id, owner_organization_id, email, email_lower, is_active, created_date)
        VALUES (?, ?, ?, ?, TRUE, now())
    """.trimIndent(), participantId, fixture.template.organizationId, email, email
    )
    val shareId = UUID.randomUUID()
    execute(
        connection, """
        INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
        VALUES (?, 'INFORMATION_REQUEST', ?, 'PARTICIPANT', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
    """.trimIndent(), shareId, fixture.requestId, participantId
    )
    execute(
        connection,
        "UPDATE information_request_party SET principal_kind = 'PARTICIPANT', principal_id = ?, share_id = ? WHERE id = ?",
        participantId,
        shareId,
        fixture.contributorPartyId,
    )
    return participantId
}
