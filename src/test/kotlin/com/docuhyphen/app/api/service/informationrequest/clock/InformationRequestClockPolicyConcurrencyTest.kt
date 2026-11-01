package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.entity.InformationRequestClockDueEffect
import com.docuhyphen.app.api.model.entity.InformationRequestClockType
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.clock.DefineInformationRequestClockPolicyCommand
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockPolicyDefinition
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestWorkingPeriod
import com.docuhyphen.app.api.model.informationrequest.clock.PublishInformationRequestClockPolicyVersionCommand
import com.docuhyphen.app.api.repository.informationrequest.clock.*
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.communication.CommunicationResolver
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import io.quarkus.arc.Arc
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.DayOfWeek
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestClockPolicyConcurrencyTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var policyRepository: InformationRequestClockPolicyRepository

    @Inject
    lateinit var versionRepository: InformationRequestClockPolicyVersionRepository

    @Inject
    lateinit var periodRepository: InformationRequestClockPolicyPeriodRepository

    @Inject
    lateinit var holidayRepository: InformationRequestClockPolicyHolidayRepository

    @Inject
    lateinit var reminderRepository: InformationRequestClockPolicyReminderRepository

    @Inject
    lateinit var auditRecorder: AuditRecorder

    @Inject
    lateinit var actorLabels: InformationRequestAuditActorLabelResolver

    @Inject
    lateinit var communications: CommunicationResolver

    @Inject
    lateinit var commandReceiptService: CommandReceiptService

    @Test
    fun `two publications racing on one policy take consecutive version numbers`() {
        val service = service(insertUser())
        val policyId = QuarkusTransaction.requiringNew().call {
            service.define(
                DefineInformationRequestClockPolicyCommand(
                    "raced-window",
                    "Raced window",
                    definition(480),
                    "define-raced-window"
                )
            )
        }.policy.id
        val firstHoldsLock = CountDownLatch(1)

        val first = CompletableFuture.supplyAsync {
            runCatching {
                withRequestContext {
                    QuarkusTransaction.requiringNew().run {
                        service.publishVersion(
                            PublishInformationRequestClockPolicyVersionCommand(
                                policyId,
                                definition(240),
                                "publish-first"
                            )
                        )
                        firstHoldsLock.countDown()
                        Thread.sleep(HOLD_MILLIS)
                    }
                }
            }.exceptionOrNull()
        }
        firstHoldsLock.await(30, TimeUnit.SECONDS)
        val second = CompletableFuture.supplyAsync {
            runCatching {
                withRequestContext {
                    QuarkusTransaction.requiringNew().run {
                        service.publishVersion(
                            PublishInformationRequestClockPolicyVersionCommand(
                                policyId,
                                definition(120),
                                "publish-second"
                            )
                        )
                    }
                }
            }.exceptionOrNull()
        }

        assertNull(first.get(60, TimeUnit.SECONDS))
        assertNull(second.get(60, TimeUnit.SECONDS))
        dataSource.connection.use { connection ->
            assertEquals(
                "1:480,2:240,3:120",
                queryString(
                    connection,
                    """
                    SELECT string_agg(version_number || ':' || urgent_duration_minutes, ',' ORDER BY version_number)
                    FROM information_request_clock_policy_version WHERE policy_id = ?
                    """.trimIndent(),
                    policyId,
                ),
            )
        }
    }

    @Test
    fun `two definitions racing for one key leave one policy and refuse the other with the key-taken code`() {
        val owner = insertUser()
        val service = service(owner)
        val firstHoldsKey = CountDownLatch(1)

        val first = CompletableFuture.supplyAsync {
            runCatching {
                withRequestContext {
                    QuarkusTransaction.requiringNew().run {
                        service.define(
                            DefineInformationRequestClockPolicyCommand(
                                "contended-window",
                                "First window",
                                definition(480),
                                "define-first"
                            )
                        )
                        firstHoldsKey.countDown()
                        Thread.sleep(HOLD_MILLIS)
                    }
                }
            }.exceptionOrNull()
        }
        firstHoldsKey.await(30, TimeUnit.SECONDS)
        val second = CompletableFuture.supplyAsync {
            runCatching {
                withRequestContext {
                    QuarkusTransaction.requiringNew().run {
                        service.define(
                            DefineInformationRequestClockPolicyCommand(
                                "contended-window",
                                "Second window",
                                definition(240),
                                "define-second"
                            )
                        )
                    }
                }
            }.exceptionOrNull()
        }

        assertNull(first.get(60, TimeUnit.SECONDS))
        val refusal = second.get(60, TimeUnit.SECONDS)
        assertEquals(
            InformationRequestErrorCatalog.CLOCK_POLICY_KEY_TAKEN,
            (refusal as InformationRequestLifecycleException).reasonCode
        )
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_clock_policy WHERE owner_user_id = ?",
                    owner
                )
            )
            assertEquals(
                "First window",
                queryString(
                    connection,
                    "SELECT display_name FROM information_request_clock_policy WHERE owner_user_id = ?",
                    owner
                ),
            )
        }
    }

    private fun <T> withRequestContext(block: () -> T): T {
        val context = Arc.container().requestContext()
        context.activate()
        try {
            return block()
        } finally {
            context.terminate()
        }
    }

    private fun service(owner: UUID): InformationRequestClockPolicyService {
        val ownerRef = InformationRequestOwnerRef(InformationRequestOwnerType.USER, owner)
        val access = mock<InformationRequestOwnerScopeAccess>()
        whenever(access.currentOwner()).thenReturn(ownerRef)
        whenever(access.requireAccess(any(), any<Action>())).thenReturn(
            RequestAccessContext(
                PrincipalRef.user(owner),
                AuthorizationContext(sessionRef = "owner-session")
            )
        )
        return InformationRequestClockPolicyService(
            access, policyRepository, versionRepository, periodRepository, holidayRepository, reminderRepository,
            auditRecorder, actorLabels, communications, Clock.systemUTC(), commandReceiptService,
        )
    }

    private fun definition(urgentMinutes: Int) = InformationRequestClockPolicyDefinition(
        clockType = InformationRequestClockType.BUSINESS,
        businessTimezone = "UTC",
        workingPeriods = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)
            .map { InformationRequestWorkingPeriod(it, 540, 1020) },
        holidays = emptyList(),
        standardDurationMinutes = 2400,
        urgentDurationMinutes = urgentMinutes,
        reminderMinutesBeforeDue = listOf(60),
        escalationAfterMinutes = null,
        dueEffect = InformationRequestClockDueEffect.MARK_OVERDUE,
    )

    private fun insertUser(): UUID {
        val id = UUID.randomUUID()
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
                "clock-policy-${id.toString().take(8)}@process.test",
            )
        }
        return id
    }

    private companion object {
        const val HOLD_MILLIS = 1_500L
    }
}
