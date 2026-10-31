package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.SubjectIdentityOwnerType
import com.docuhyphen.app.api.model.entity.SubjectIdentityRef
import com.docuhyphen.app.api.model.entity.SubjectKind
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestEntityMappingTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var entityManager: EntityManager

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var participants: com.docuhyphen.app.api.service.exchange.ExternalParticipantService

    @Test
    fun `concurrent first contact creation reuses the committed participant`() {
        val owner = dataSource.connection.use { SubmissionRuntimeSqlFixture(it).template.organizationId }
        val label = "participant-create-${UUID.randomUUID()}"
        val email = "${UUID.randomUUID()}@example.test"
        val inserted = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        fun create(hold: Boolean) = executor.submit(Callable {
            val context = io.quarkus.arc.Arc.container().requestContext()
            context.activate()
            try {
                QuarkusTransaction.requiringNew().call {
                    if (!hold) entityManager.createNativeQuery("SELECT set_config('application_name', :label, true)")
                        .setParameter("label", label).singleResult
                    val participant = participants.findOrCreate(
                        com.docuhyphen.app.api.model.exchange.ExternalParticipantOwner.Organization(owner),
                        email,
                        "Participant",
                    )
                    entityManager.flush()
                    if (hold) {
                        inserted.countDown()
                        check(release.await(20, TimeUnit.SECONDS))
                    }
                    participant.id
                }
            } finally {
                context.terminate()
            }
        })
        try {
            val first = create(true)
            check(inserted.await(10, TimeUnit.SECONDS))
            val second = create(false)
            fun waiting() = dataSource.connection.use { observer ->
                queryInt(
                    observer,
                    "SELECT count(*) FROM pg_stat_activity WHERE application_name = ? AND wait_event_type = 'Lock'",
                    label
                )
            }

            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (waiting() == 0 && !second.isDone && System.nanoTime() < deadline) Thread.sleep(10)
            if (second.isDone) second.get(1, TimeUnit.SECONDS)
            assertEquals(1, waiting())
            release.countDown()
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `a request lock observes the parent policy committed while its lock was waiting`() {
        val fixture = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        val label = "request-parent-${UUID.randomUUID()}"
        val executor = Executors.newSingleThreadExecutor()
        try {
            dataSource.connection.use { writer ->
                writer.autoCommit = false
                try {
                    queryString(writer, "SELECT id::text FROM exchange WHERE id = ? FOR UPDATE", fixture.exchangeId)
                    execute(
                        writer,
                        "UPDATE exchange SET require_recipient_sign_in = TRUE WHERE id = ?",
                        fixture.exchangeId
                    )
                    val reader = executor.submit(Callable {
                        val context = io.quarkus.arc.Arc.container().requestContext()
                        context.activate()
                        try {
                            QuarkusTransaction.requiringNew().call {
                                entityManager.createNativeQuery("SELECT set_config('application_name', :label, true)")
                                    .setParameter("label", label).singleResult
                                requests.findRequestByIdForUpdate(fixture.requestId)
                                entityManager.find(Exchange::class.java, fixture.exchangeId).requireRecipientSignIn
                            }
                        } finally {
                            context.terminate()
                        }
                    })

                    fun waiting() = dataSource.connection.use { observer ->
                        queryInt(
                            observer,
                            "SELECT count(*) FROM pg_stat_activity WHERE application_name = ? AND wait_event_type = 'Lock'",
                            label
                        )
                    }

                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                    while (waiting() == 0 && !reader.isDone && System.nanoTime() < deadline)
                        Thread.sleep(10)
                    if (reader.isDone) reader.get(1, TimeUnit.SECONDS)
                    assertEquals(1, waiting())
                    writer.commit()
                    assertEquals(true, reader.get(10, TimeUnit.SECONDS))
                } finally {
                    writer.rollback()
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `a newly persisted subject exposes its generated owner identity in the same transaction`() {
        val owner = dataSource.connection.use { SubmissionRuntimeSqlFixture(it).template.organizationId }
        QuarkusTransaction.requiringNew().run {
            val subject = SubjectIdentityRef().apply {
                ownerType = SubjectIdentityOwnerType.ORGANIZATION
                ownerOrganizationId = owner
                subjectKind = SubjectKind.RECORD
            }
            entityManager.persist(subject)
            entityManager.flush()
            assertEquals(owner, subject.ownerId)
        }
    }
}
