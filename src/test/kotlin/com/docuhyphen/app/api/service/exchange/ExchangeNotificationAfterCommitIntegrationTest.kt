package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.repository.exchange.ExchangeRepositoryPostgreSQLResource
import com.docuhyphen.app.api.service.notification.UserNotificationPreference
import com.docuhyphen.app.api.service.notification.UserNotificationPreferenceService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.transaction.UserTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(ExchangeRepositoryPostgreSQLResource::class, restrictToAnnotatedClass = true)
class ExchangeNotificationAfterCommitIntegrationTest
{
    @Inject
    lateinit var service: ExchangeNotificationDeliveryService

    @Inject
    lateinit var transaction: UserTransaction

    @Inject
    lateinit var dataSource: DataSource

    private lateinit var preferenceService: UserNotificationPreferenceService
    private lateinit var realtimeEventService: RealtimeEventService

    private val appUserId = UUID.fromString("81000000-0000-0000-0000-000000000001")
    private val exchangeId = UUID.fromString("82000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setUp()
    {
        preferenceService = mock()
        realtimeEventService = mock()
        QuarkusMock.installMockForType(preferenceService, UserNotificationPreferenceService::class.java)
        QuarkusMock.installMockForType(realtimeEventService, RealtimeEventService::class.java)
        whenever(
            preferenceService.isEnabled(
                appUserId,
                UserNotificationPreference.EXCHANGE_INITIATED,
                NotificationChannelType.IN_APP,
            ),
        ).thenReturn(true)
        insertAppUser()
    }

    @Test
    fun `committed callback persists in app notification in a new transaction`()
    {
        transaction.begin()
        service.scheduleAfterCommit(
            exchangeId = exchangeId,
            exchangeStatus = "INITIATED",
            emails = emptyList(),
            inAppNotifications = listOf(
                ExchangeInAppDelivery(
                    appUserId = appUserId,
                    type = "exchange.recipient_invitation",
                    title = "Invitation",
                    message = "A committed Exchange is ready.",
                    data = mapOf("exchangeId" to exchangeId.toString()),
                ),
            ),
            refreshAppUserIds = emptySet(),
        )
        transaction.commit()

        assertEquals(1, notificationCount())
        verify(realtimeEventService).broadcastNotificationToUser(eq(appUserId), org.mockito.kotlin.any())
    }

    private fun insertAppUser()
    {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """INSERT INTO app_user
                   (id, is_active, created_date, email, email_verification_completed, is_temporary,
                    sign_in_attempts, exchange_version, multifactor_authentication_type,
                    is_password_temporary, email_mfa_fallback_enabled)
                   VALUES (?, TRUE, ?, 'after-commit@example.test', TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)""",
            ).use { statement ->
                statement.setObject(1, appUserId)
                statement.setTimestamp(2, Timestamp.from(Instant.now()))
                statement.executeUpdate()
            }
        }
    }

    private fun notificationCount(): Int
    {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """SELECT COUNT(*)
                   FROM in_app_notification
                   WHERE app_user_id = ?
                     AND event_type = 'exchange.recipient_invitation'
                     AND payload_json LIKE ?""",
            ).use { statement ->
                statement.setObject(1, appUserId)
                statement.setString(2, "%$exchangeId%")
                statement.executeQuery().use { result ->
                    result.next()
                    return result.getInt(1)
                }
            }
        }
    }
}
