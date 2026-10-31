package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationNotFoundException
import com.docuhyphen.app.api.exception.RecordPreservationRequestException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationHoldView
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class RecordPreservationHoldTargetTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var administration: RecordPreservationAdministration

    @Test
    fun `a hold on a record type that is never preserved is refused without a hold`() {
        val owner = subscribedUser()
        actAs(owner)

        assertThrows(RecordPreservationRequestException::class.java) {
            place("UNPRESERVED_RECORD", UUID.randomUUID().toString(), "unpreserved-type")
        }

        assertEquals(0, holdsOf(owner))
    }

    @Test
    fun `a hold on another owner's record is refused as not found without a hold`() {
        val owner = subscribedUser()
        val foreignExchange = exchangeOf(subscribedUser())
        actAs(owner)

        assertThrows(RecordPreservationNotFoundException::class.java) {
            place("EXCHANGE", foreignExchange.toString(), "foreign-record")
        }

        assertEquals(0, holdsOf(owner))
    }

    @Test
    fun `a hold on the owner's own record is placed`() {
        val owner = subscribedUser()
        val exchangeId = exchangeOf(owner)
        actAs(owner)

        val placed = place("EXCHANGE", exchangeId.toString(), "own-record")

        assertEquals(exchangeId.toString(), placed.hold.resourceId)
        assertEquals(1, holdsOf(owner))
    }

    private fun place(resourceType: String, resourceId: String, key: String): RecordPreservationHoldView =
        QuarkusTransaction.requiringNew().call {
            administration.placeHold(
                resourceType,
                resourceId,
                RecordPreservationScope.RESOURCE,
                "Preserve the record",
                null,
                null,
                key
            )
        }

    private fun subscribedUser(): UUID = UUID.randomUUID().also { id ->
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO app_user
                    (id, is_active, created_date, email, email_verification_completed, is_temporary,
                     sign_in_attempts, exchange_version, multifactor_authentication_type,
                     is_password_temporary, email_mfa_fallback_enabled)
                VALUES (?, TRUE, now(), ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(),
                id,
                "records-${id.toString().take(8)}@process.test",
            )
            execute(
                connection,
                "INSERT INTO user_subscription_policy (id, app_user_id, plan_code, subscription_status) VALUES (?, ?, 'PERSONAL', 'ACTIVE')",
                UUID.randomUUID(),
                id,
            )
        }
    }

    private fun exchangeOf(owner: UUID): UUID = UUID.randomUUID().also { id ->
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO exchange (id, owner_user_id, initiator_id, is_deleted, require_recipient_sign_in,
                    created_date, last_activity, description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, now(), now(), 'Collect records', 'Please respond', 'Record collection', 'INITIATED')
                """.trimIndent(),
                id,
                owner,
                owner,
            )
        }
    }

    private fun holdsOf(owner: UUID): Int = dataSource.connection.use { connection ->
        queryInt(connection, "SELECT count(*) FROM audit_legal_hold WHERE owner_kind = 'USER' AND owner_id = ?", owner)
    }

    private fun actAs(userId: UUID) {
        val authTokenContext = mock<AuthTokenContext>()
        whenever(authTokenContext.authToken).thenReturn(AuthToken().apply { appUser = AppUser().apply { id = userId } })
        QuarkusMock.installMockForType(authTokenContext, AuthTokenContext::class.java)
        QuarkusMock.installMockForType(mock<AdminActionGuardService>(), AdminActionGuardService::class.java)
    }
}
