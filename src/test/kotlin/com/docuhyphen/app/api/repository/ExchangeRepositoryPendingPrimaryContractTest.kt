package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

private class PendingPrimaryPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<PendingPrimaryPostgreSQLContainer>(imageName)

class ExchangeRepositoryPostgreSQLResource : QuarkusTestResourceLifecycleManager
{
    private val postgres = PendingPrimaryPostgreSQLContainer("postgres:17")
        .withDatabaseName("docuhyphen_pending_primary_test")
        .withUsername("docuhyphen")
        .withPassword("docuhyphen")

    override fun start(): Map<String, String>
    {
        postgres.start()
        return mapOf(
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "file.storage.service" to "local",
            "app.secrets.rotation.enabled" to "false",
            "quarkus.kafka.devservices.enabled" to "false",
        )
    }

    override fun stop()
    {
        postgres.stop()
    }
}

@QuarkusTest
@QuarkusTestResource(ExchangeRepositoryPostgreSQLResource::class, restrictToAnnotatedClass = true)
class ExchangeRepositoryPendingPrimaryContractTest
{
    @Inject
    lateinit var repository: ExchangeRepository

    @Inject
    lateinit var exchangeRecipientRepository: ExchangeRecipientRepository

    @Inject
    lateinit var dataSource: DataSource

    private val now = Timestamp.from(Instant.now().minusSeconds(24 * 60 * 60))
    private val organizationId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val initiatorId = UUID.fromString("20000000-0000-0000-0000-000000000001")
    private val ownerId = UUID.fromString("30000000-0000-0000-0000-000000000001")
    private val managerId = UUID.fromString("30000000-0000-0000-0000-000000000002")
    private val memberId = UUID.fromString("30000000-0000-0000-0000-000000000003")
    private val observerId = UUID.fromString("30000000-0000-0000-0000-000000000004")
    private val inactiveOwnerId = UUID.fromString("30000000-0000-0000-0000-000000000005")
    private val unrelatedId = UUID.fromString("30000000-0000-0000-0000-000000000006")
    private val directUserId = UUID.fromString("30000000-0000-0000-0000-000000000007")
    private val groupId = UUID.fromString("40000000-0000-0000-0000-000000000001")
    private val ownerExchangeId = UUID.fromString("50000000-0000-0000-0000-000000000001")
    private val managerExchangeId = UUID.fromString("50000000-0000-0000-0000-000000000002")
    private val directUserExchangeId = UUID.fromString("50000000-0000-0000-0000-000000000003")
    private val archivedExchangeId = UUID.fromString("50000000-0000-0000-0000-000000000004")
    private val revokedBeforeEndExchangeId = UUID.fromString("50000000-0000-0000-0000-000000000005")
    private val expiredBeforeEndExchangeId = UUID.fromString("50000000-0000-0000-0000-000000000006")

    @Test
    fun `repository queries expose pending group primaries only to active owners and managers`()
    {
        dataSource.connection.use(::resetAndSeedFixtures)

        val groupInvitations = setOf(ownerExchangeId, managerExchangeId)
        assertAccessible(ownerId, groupInvitations)
        assertAccessible(managerId, groupInvitations)
        assertAccessible(directUserId, setOf(directUserExchangeId))
        assertEquals(1, exchangeRecipientRepository.findPendingTrustedParticipantsFor(ownerId).size)
        assertEquals(1, exchangeRecipientRepository.findPendingTrustedParticipantsFor(managerId).size)
        assertEquals(emptyList<com.docuhyphen.app.api.model.entity.ExchangeRecipient>(),
            exchangeRecipientRepository.findPendingTrustedParticipantsFor(directUserId))

        listOf(memberId, observerId, inactiveOwnerId, unrelatedId).forEach { ineligibleUserId ->
            assertFalse(repository.userHasExchanges(ineligibleUserId))
            assertEquals(emptySet<UUID>(), repository.findByParticipatingAppUser(ineligibleUserId).ids())
            assertEquals(emptySet<UUID>(), repository.getAppUserLinkedExchanges(ineligibleUserId).ids())
            assertEquals(
                emptySet<UUID>(),
                repository.searchSessions(
                    ineligibleUserId,
                    "Pending primary",
                    null,
                    null,
                    0,
                    100,
                    "createdDate",
                    "ASC",
                ).ids(),
            )
            assertEquals(
                0,
                repository.countSearchResults(ineligibleUserId, "Pending primary", null, null),
            )
            assertEquals(
                emptyList<com.docuhyphen.app.api.model.entity.ExchangeRecipient>(),
                exchangeRecipientRepository.findPendingTrustedParticipantsFor(ineligibleUserId),
            )
        }
    }

    @Test
    fun `archive queries preserve access held at the terminal timestamp only`()
    {
        dataSource.connection.use { connection ->
            resetAndSeedFixtures(connection)
            val endedAt = Timestamp.from(now.toInstant().plusSeconds(12 * 60 * 60))

            insertArchivedExchange(connection, archivedExchangeId, endedAt)
            insertShare(
                connection,
                UUID.randomUUID(),
                archivedExchangeId,
                directUserId,
                "USER",
                "REVOKED",
                null,
                Timestamp.from(endedAt.toInstant().plusSeconds(1)),
            )

            insertArchivedExchange(connection, revokedBeforeEndExchangeId, endedAt)
            insertShare(
                connection,
                UUID.randomUUID(),
                revokedBeforeEndExchangeId,
                memberId,
                "USER",
                "REVOKED",
                null,
                Timestamp.from(endedAt.toInstant().minusSeconds(1)),
            )

            insertArchivedExchange(connection, expiredBeforeEndExchangeId, endedAt)
            insertShare(
                connection,
                UUID.randomUUID(),
                expiredBeforeEndExchangeId,
                observerId,
                "USER",
                "ACTIVE",
                Timestamp.from(endedAt.toInstant().minusSeconds(1)),
            )
        }

        assertEquals(
            setOf(archivedExchangeId),
            repository.searchSessions(
                directUserId,
                null,
                listOf(ExchangeStatus.ENDED),
                null,
                0,
                100,
                "createdDate",
                "ASC",
            ).ids(),
        )
        assertEquals(
            1L,
            repository.countSearchResults(
                directUserId,
                null,
                listOf(ExchangeStatus.ENDED),
                null,
            ),
        )
        assertTrue(repository.hasHistoricalArchiveAccess(archivedExchangeId, directUserId))
        assertFalse(repository.hasHistoricalArchiveAccess(revokedBeforeEndExchangeId, memberId))
        assertFalse(repository.hasHistoricalArchiveAccess(expiredBeforeEndExchangeId, observerId))
        assertFalse(repository.hasHistoricalArchiveAccess(archivedExchangeId, unrelatedId))
    }

    @Test
    fun `primary acceptance row lock serializes competing decisions`()
    {
        dataSource.connection.use(::resetAndSeedFixtures)

        assertCompetingTransactionWaits {
            checkNotNull(exchangeRecipientRepository.findPrimaryForUpdate(ownerExchangeId))
        }
    }

    @Test
    fun `participant decision row lock serializes competing decisions`()
    {
        dataSource.connection.use(::resetAndSeedFixtures)
        val participantId = exchangeRecipientRepository.findPendingTrustedParticipantsFor(ownerId)
            .single()
            .id

        assertCompetingTransactionWaits {
            checkNotNull(exchangeRecipientRepository.findByIdForUpdate(participantId))
        }
    }

    private fun assertCompetingTransactionWaits(lockRecipient: () -> Unit)
    {
        val firstTransactionLocked = CountDownLatch(1)
        val releaseFirstTransaction = CountDownLatch(1)
        val secondTransactionLocked = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try
        {
            val first = executor.submit {
                QuarkusTransaction.requiringNew().run {
                    lockRecipient()
                    firstTransactionLocked.countDown()
                    check(releaseFirstTransaction.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(firstTransactionLocked.await(5, TimeUnit.SECONDS))

            val second = executor.submit {
                QuarkusTransaction.requiringNew().run {
                    lockRecipient()
                    secondTransactionLocked.countDown()
                }
            }

            assertFalse(secondTransactionLocked.await(300, TimeUnit.MILLISECONDS))
            releaseFirstTransaction.countDown()
            first.get(5, TimeUnit.SECONDS)
            assertTrue(secondTransactionLocked.await(5, TimeUnit.SECONDS))
            second.get(5, TimeUnit.SECONDS)
        }
        finally
        {
            releaseFirstTransaction.countDown()
            executor.shutdownNow()
        }
    }

    private fun resetAndSeedFixtures(connection: Connection)
    {
        deleteFixtures(connection)
        seedFixtures(connection)
    }

    private fun deleteFixtures(connection: Connection)
    {
        listOf(
            "exchange_recipient",
            "share",
        ).forEach { table ->
            connection.prepareStatement(
                """DELETE FROM $table
                   WHERE ${if (table == "share") "resource_id" else "exchange_id"} IN
                       (SELECT id FROM exchange WHERE owner_organization_id = ?)""",
            ).use { statement ->
                statement.setObject(1, organizationId)
                statement.executeUpdate()
            }
        }
        connection.prepareStatement(
            "DELETE FROM exchange WHERE owner_organization_id = ?",
        ).use { statement ->
            statement.setObject(1, organizationId)
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "DELETE FROM principal_group_member WHERE principal_group_id = ?",
        ).use { statement ->
            statement.setObject(1, groupId)
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "DELETE FROM principal_group WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, groupId)
            statement.executeUpdate()
        }
        val userIds = listOf(
            initiatorId,
            ownerId,
            managerId,
            memberId,
            observerId,
            inactiveOwnerId,
            unrelatedId,
            directUserId,
        )
        connection.prepareStatement(
            "DELETE FROM app_user WHERE id IN (${userIds.joinToString { "?" }})",
        ).use { statement ->
            userIds.forEachIndexed { index, userId -> statement.setObject(index + 1, userId) }
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "DELETE FROM organization WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, organizationId)
            statement.executeUpdate()
        }
    }

    private fun assertAccessible(userId: UUID, expected: Set<UUID>)
    {
        assertTrue(repository.userHasExchanges(userId))
        assertEquals(expected, repository.findByParticipatingAppUser(userId).ids())
        assertEquals(expected, repository.getAppUserLinkedExchanges(userId).ids())
        assertEquals(
            expected,
            repository.searchSessions(
                userId,
                "Pending primary",
                null,
                null,
                0,
                100,
                "createdDate",
                "ASC",
            ).ids(),
        )
        assertEquals(
            expected.size.toLong(),
            repository.countSearchResults(userId, "Pending primary", null, null),
        )
    }

    private fun seedFixtures(connection: Connection)
    {
        insertOrganization(connection)
        listOf(
            initiatorId,
            ownerId,
            managerId,
            memberId,
            observerId,
            inactiveOwnerId,
            unrelatedId,
            directUserId,
        ).forEachIndexed { index, userId -> insertUser(connection, userId, index) }
        insertGroup(connection)
        insertGroupMember(connection, ownerId, "OWNER", true)
        insertGroupMember(connection, managerId, "MANAGER", true)
        insertGroupMember(connection, memberId, "MEMBER", true)
        insertGroupMember(connection, observerId, "OBSERVER", true)
        insertGroupMember(connection, inactiveOwnerId, "OWNER", false)

        insertPendingPrimary(connection, ownerExchangeId, groupId, "PRINCIPAL_GROUP")
        insertPendingPrimary(connection, managerExchangeId, groupId, "PRINCIPAL_GROUP")
        insertPendingPrimary(connection, directUserExchangeId, directUserId, "USER")
        insertPendingParticipant(connection)
        insertRevokedPrimary(connection)
        insertExpiredPrimary(connection)
    }

    private fun insertOrganization(connection: Connection)
    {
        connection.prepareStatement(
            """INSERT INTO organization
               (id, is_active, verification_complete, created_date, name, registration_number)
               VALUES (?, TRUE, TRUE, ?, 'Pending primary organization', 'PENDING-PRIMARY')""",
        ).use { statement ->
            statement.setObject(1, organizationId)
            statement.setTimestamp(2, now)
            statement.executeUpdate()
        }
    }

    private fun insertUser(connection: Connection, userId: UUID, index: Int)
    {
        connection.prepareStatement(
            """INSERT INTO app_user
               (id, is_active, created_date, email, email_verification_completed, is_temporary,
                sign_in_attempts, exchange_version, multifactor_authentication_type,
                is_password_temporary, email_mfa_fallback_enabled)
               VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)""",
        ).use { statement ->
            statement.setObject(1, userId)
            statement.setTimestamp(2, now)
            statement.setString(3, "pending-primary-$index@example.test")
            statement.executeUpdate()
        }
    }

    private fun insertGroup(connection: Connection)
    {
        connection.prepareStatement(
            """INSERT INTO principal_group
               (id, name, scope, owner_organization_id, externally_published, is_active, created_date)
               VALUES (?, 'Published decision group', 'ORG', ?, TRUE, TRUE, ?)""",
        ).use { statement ->
            statement.setObject(1, groupId)
            statement.setObject(2, organizationId)
            statement.setTimestamp(3, now)
            statement.executeUpdate()
        }
    }

    private fun insertGroupMember(connection: Connection, userId: UUID, role: String, active: Boolean)
    {
        connection.prepareStatement(
            """INSERT INTO principal_group_member
               (id, principal_group_id, principal_kind, principal_id, group_role, added_at, is_active)
               VALUES (?, ?, 'USER', ?, ?, ?, ?)""",
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, groupId)
            statement.setObject(3, userId)
            statement.setString(4, role)
            statement.setTimestamp(5, now)
            statement.setBoolean(6, active)
            statement.executeUpdate()
        }
    }

    private fun insertPendingPrimary(
        connection: Connection,
        exchangeId: UUID,
        principalId: UUID,
        principalKind: String,
    )
    {
        val shareId = UUID.randomUUID()
        insertExchange(connection, exchangeId)
        insertShare(connection, shareId, exchangeId, principalId, principalKind, "PENDING_APPROVAL", null)
        val selectionType = if (principalKind == "USER") "TRUSTED_PERSON" else "TRUSTED_GROUP"
        insertRecipient(connection, exchangeId, shareId, "PRIMARY", selectionType)
    }

    private fun insertPendingParticipant(connection: Connection)
    {
        val exchangeId = UUID.randomUUID()
        val shareId = UUID.randomUUID()
        insertExchange(connection, exchangeId)
        insertShare(connection, shareId, exchangeId, groupId, "PRINCIPAL_GROUP", "PENDING_APPROVAL", null)
        insertRecipient(connection, exchangeId, shareId, "PARTICIPANT", "TRUSTED_GROUP")
    }

    private fun insertRevokedPrimary(connection: Connection)
    {
        val exchangeId = UUID.randomUUID()
        val shareId = UUID.randomUUID()
        insertExchange(connection, exchangeId)
        insertShare(connection, shareId, exchangeId, groupId, "PRINCIPAL_GROUP", "REVOKED", null)
        insertRecipient(connection, exchangeId, shareId, "PRIMARY", "TRUSTED_GROUP")
    }

    private fun insertExpiredPrimary(connection: Connection)
    {
        val exchangeId = UUID.randomUUID()
        val shareId = UUID.randomUUID()
        insertExchange(connection, exchangeId)
        insertShare(
            connection,
            shareId,
            exchangeId,
            groupId,
            "PRINCIPAL_GROUP",
            "PENDING_APPROVAL",
            Timestamp.from(now.toInstant().plusSeconds(60 * 60)),
        )
        insertRecipient(connection, exchangeId, shareId, "PRIMARY", "TRUSTED_GROUP")
    }

    private fun insertExchange(connection: Connection, exchangeId: UUID)
    {
        connection.prepareStatement(
            """INSERT INTO exchange
               (id, is_deleted, require_recipient_sign_in, created_date, last_activity, initiator_id,
                owner_organization_id, description, initial_share_message, name, status,
                no_auth_access_validity_days)
               VALUES (?, FALSE, TRUE, ?, ?, ?, ?, 'Description', 'Message', ?, 'INITIATED', 7)""",
        ).use { statement ->
            statement.setObject(1, exchangeId)
            statement.setTimestamp(2, now)
            statement.setTimestamp(3, now)
            statement.setObject(4, initiatorId)
            statement.setObject(5, organizationId)
            statement.setString(6, "Pending primary $exchangeId")
            statement.executeUpdate()
        }
    }

    private fun insertArchivedExchange(connection: Connection, exchangeId: UUID, endedAt: Timestamp)
    {
        connection.prepareStatement(
            """INSERT INTO exchange
               (id, is_deleted, require_recipient_sign_in, created_date, last_activity, initiator_id,
                owner_organization_id, description, initial_share_message, name, status, end_date,
                no_auth_access_validity_days)
               VALUES (?, FALSE, TRUE, ?, ?, ?, ?, 'Description', 'Message', ?, 'ENDED', ?, 7)""",
        ).use { statement ->
            statement.setObject(1, exchangeId)
            statement.setTimestamp(2, now)
            statement.setTimestamp(3, endedAt)
            statement.setObject(4, initiatorId)
            statement.setObject(5, organizationId)
            statement.setString(6, "Archived Exchange $exchangeId")
            statement.setTimestamp(7, endedAt)
            statement.executeUpdate()
        }
    }

    private fun insertShare(
        connection: Connection,
        shareId: UUID,
        exchangeId: UUID,
        principalId: UUID,
        principalKind: String,
        status: String,
        expiresAt: Timestamp?,
        revokedAt: Timestamp? = null,
    )
    {
        connection.prepareStatement(
            """INSERT INTO share
               (id, resource_type, resource_id, principal_kind, principal_id, role_name, source,
                status, granted_at, expires_at, revoked_at)
               VALUES (?, 'EXCHANGE', ?, ?, ?, 'VIEWER', 'DIRECT', ?, ?, ?, ?)""",
        ).use { statement ->
            statement.setObject(1, shareId)
            statement.setObject(2, exchangeId)
            statement.setString(3, principalKind)
            statement.setObject(4, principalId)
            statement.setString(5, status)
            statement.setTimestamp(6, now)
            statement.setTimestamp(7, expiresAt)
            statement.setTimestamp(8, revokedAt)
            statement.executeUpdate()
        }
    }

    private fun insertRecipient(
        connection: Connection,
        exchangeId: UUID,
        shareId: UUID,
        purpose: String,
        selectionType: String,
    )
    {
        connection.prepareStatement(
            """INSERT INTO exchange_recipient
               (id, exchange_id, direct_share_id, purpose, selection_type, target_organization_id,
                acceptance_status, created_at)
               VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)""",
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, exchangeId)
            statement.setObject(3, shareId)
            statement.setString(4, purpose)
            statement.setString(5, selectionType)
            statement.setObject(6, organizationId)
            statement.setTimestamp(7, now)
            statement.executeUpdate()
        }
    }

    private fun List<com.docuhyphen.app.api.model.entity.Exchange>.ids(): Set<UUID> = map { it.id }.toSet()
}
