package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class OrganizationMemberRemovalHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

    @Test
    fun `removing a member with retained personal records is refused before deleting their membership`() {
        val memberId = UUID.randomUUID()
        val fixture = dataSource.connection.use { connection ->
            val runtime = SubmissionRuntimeSqlFixture(connection)
            runtime.insertUser(memberId)
            listOf(runtime.template.userId, memberId).forEach { userId ->
                val personId = UUID.randomUUID()
                execute(
                    connection,
                    "INSERT INTO person (id, first_name, last_name, created_date) VALUES (?, 'Process', 'Member', now())",
                    personId
                )
                execute(
                    connection,
                    "UPDATE app_user SET person_id = ? WHERE id = ?", personId, userId
                )
            }
            listOf(runtime.template.userId to "ORG_ADMIN", memberId to "ORG_MEMBER").forEach { (userId, role) ->
                val membershipId = UUID.randomUUID()
                execute(
                    connection, """
                    INSERT INTO organization_membership
                        (id, app_user_id, organization_id, status, is_primary, joined_at, created_date)
                    VALUES (?, ?, ?, 'ACTIVE', FALSE, now(), now())
                """.trimIndent(), membershipId, userId, runtime.template.organizationId
                )
                execute(
                    connection,
                    "INSERT INTO organization_membership_role (organization_membership_id, role_name) VALUES (?, ?)",
                    membershipId, role
                )
            }
            execute(
                connection, """
                INSERT INTO external_participant (id, owner_app_user_id, email, email_lower, is_active, created_date)
                VALUES (?, ?, 'contact@example.test', 'contact@example.test', TRUE, now())
            """.trimIndent(), UUID.randomUUID(), memberId
            )
            runtime
        }
        val caller = InformationRequestSignedInCaller.signIn(appUsers, fixture.template.userId)
        val path = "/organizations/${fixture.template.organizationId}/app-users/$memberId"
        val preflight = caller.authorized().queryParam("check", "DELETABLE").get(path)
        val removal = caller.authorized().delete(path)
        assertAll(
            { assertEquals(409, preflight.statusCode, preflight.asString()) },
            { assertEquals(400, removal.statusCode, removal.asString()) },
            {
                dataSource.connection.use { connection ->
                    assertEquals(
                        1, queryInt(
                            connection,
                            "SELECT count(*) FROM organization_membership WHERE app_user_id = ? AND organization_id = ?",
                            memberId, fixture.template.organizationId
                        )
                    )
                }
            },
        )
    }
}
