package com.docuhyphen.app.api.migration

import java.sql.Connection
import java.util.*

internal class TrustedGroupSqlFixture(
    connection: Connection,
    val ownerOrganizationId: UUID,
    private val actorId: UUID,
) {
    val partnerOrganizationId: UUID = UUID.randomUUID()
    val relationshipId: UUID = UUID.randomUUID()
    val groupId: UUID = UUID.randomUUID()

    init {
        execute(
            connection,
            """
            INSERT INTO organization (id, name, registration_number, is_active, verification_complete, created_date)
            VALUES (?, 'Partner Organization', ?, TRUE, TRUE, now())
            """.trimIndent(),
            partnerOrganizationId,
            "REG-${partnerOrganizationId.toString().take(8)}",
        )
        val (first, second) = listOf(ownerOrganizationId, partnerOrganizationId).sortedBy { it.toString() }
        execute(
            connection,
            """
            INSERT INTO organization_trust_relationship
                (id, organization_a_id, organization_b_id, requested_by_organization_id, requested_by_app_user_id,
                 status, requested_at, request_expires_at, activated_at, review_due_at, latest_transition_by_app_user_id)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', now() - interval '2 days', now() - interval '1 day',
                    now() - interval '1 day', now() + interval '30 days', ?)
            """.trimIndent(),
            relationshipId,
            first,
            second,
            ownerOrganizationId,
            actorId,
            actorId,
        )
        listOf(ownerOrganizationId, partnerOrganizationId).forEach { organizationId ->
            execute(
                connection,
                """
                INSERT INTO organization_trust_party_policy
                    (id, relationship_id, policy_owner_organization_id, allow_exchanges_to_partner,
                     allow_exchanges_from_partner, allow_partner_member_resolution, allow_partner_group_discovery,
                     updated_at)
                VALUES (?, ?, ?, TRUE, TRUE, FALSE, TRUE, now())
                """.trimIndent(),
                UUID.randomUUID(),
                relationshipId,
                organizationId,
            )
        }
        execute(
            connection,
            """
            INSERT INTO principal_group (id, name, scope, owner_organization_id, externally_published, is_active, created_date)
            VALUES (?, 'Published review group', 'ORG', ?, TRUE, TRUE, now())
            """.trimIndent(),
            groupId,
            partnerOrganizationId,
        )
    }

    fun suspend(connection: Connection): UUID = UUID.randomUUID().also { suspensionId ->
        execute(
            connection,
            """
            INSERT INTO organization_trust_suspension
                (id, relationship_id, suspending_organization_id, reason, suspended_by_app_user_id, suspended_at)
            VALUES (?, ?, ?, 'Relationship under review', ?, now())
            """.trimIndent(),
            suspensionId,
            relationshipId,
            ownerOrganizationId,
            actorId,
        )
    }

    fun clearSuspension(connection: Connection, suspensionId: UUID) {
        execute(
            connection,
            "UPDATE organization_trust_suspension SET cleared_by_app_user_id = ?, cleared_at = now() WHERE id = ?",
            actorId,
            suspensionId,
        )
    }

    fun end(connection: Connection) {
        execute(
            connection,
            "UPDATE organization_trust_relationship SET status = 'ENDED', ended_at = now() WHERE id = ?",
            relationshipId,
        )
    }
}
