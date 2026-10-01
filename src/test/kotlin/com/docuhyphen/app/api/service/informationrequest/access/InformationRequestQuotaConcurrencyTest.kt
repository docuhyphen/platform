package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.singleFieldConfiguration
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestQuotaConcurrencyTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository
    @Inject lateinit var versionRepository: InformationRequestTemplateVersionRepository
    @Inject lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository
    @Inject lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter
    @Inject lateinit var exchangeRepository: ExchangeRepository
    @Inject lateinit var entitlementGuard: InformationRequestEntitlementGuard

    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(
            dataSource, definitionRepository, versionRepository, capabilityRepository, configurationWriter,
        )
    }

    @Test
    fun `two creations racing for a Personal owner's last open request place cannot both take it`()
    {
        val owner = store.insertOwner()
        subscribe(owner)
        val field = store.insertTextField("raced-note")
        val template = store.insertDraft(owner, "raced-collection")
        store.publish(template, singleFieldConfiguration(store.insertSchemaVersion(owner, listOf(field)), field))
        val exchangeId = insertExchange(owner)
        repeat(OPEN_BEFORE_RACE) { insertDraftRequest(exchangeId, owner, template.versionId) }
        val firstHoldsLock = CountDownLatch(1)

        val first = CompletableFuture.supplyAsync {
            runCatching {
                QuarkusTransaction.requiringNew().run {
                    entitlementGuard.requireRequestCreation(exchange(exchangeId))
                    insertDraftRequest(exchangeId, owner, template.versionId)
                    firstHoldsLock.countDown()
                    Thread.sleep(HOLD_MILLIS)
                }
            }.exceptionOrNull()
        }
        firstHoldsLock.await(30, TimeUnit.SECONDS)
        val second = CompletableFuture.supplyAsync {
            runCatching {
                QuarkusTransaction.requiringNew().run {
                    entitlementGuard.requireRequestCreation(exchange(exchangeId))
                    insertDraftRequest(exchangeId, owner, template.versionId)
                }
            }.exceptionOrNull()
        }

        assertNull(first.get(60, TimeUnit.SECONDS))
        val refusal = second.get(60, TimeUnit.SECONDS)
        assertEquals(SubscriptionDenialReason.PLAN_LIMIT_REACHED, (refusal as SubscriptionDenialException).denial.reason)
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT count(*) FROM information_request WHERE owner_user_id = ?").use { statement ->
                statement.setObject(1, owner)
                statement.executeQuery().use { rows ->
                    rows.next()
                    assertEquals(OPEN_BEFORE_RACE + 1, rows.getInt(1))
                }
            }
        }
    }

    private fun exchange(exchangeId: UUID): Exchange = requireNotNull(exchangeRepository.findById(exchangeId))

    private fun insertDraftRequest(exchangeId: UUID, owner: UUID, versionId: UUID)
    {
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_user_id, state,
                     gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at)
                VALUES (?, ?, ?, 'USER', ?, 'DRAFT', FALSE, 1, 1, now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                exchangeId,
                versionId,
                owner,
            )
        }
    }

    private fun subscribe(owner: UUID)
    {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "INSERT INTO user_subscription_policy (id, app_user_id, plan_code, subscription_status) VALUES (?, ?, 'PERSONAL', 'ACTIVE')",
                UUID.randomUUID(),
                owner,
            )
        }
    }

    private fun insertExchange(owner: UUID): UUID
    {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO exchange
                    (id, owner_user_id, initiator_id, is_deleted, require_recipient_sign_in, created_date, last_activity,
                     description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, now(), now(), 'Collect process information', 'Please respond',
                        'Process collection', 'ACCEPTED_STARTED')
                """.trimIndent(),
                id,
                owner,
                owner,
            )
        }
        return id
    }

    private companion object
    {
        const val OPEN_BEFORE_RACE = 24
        const val HOLD_MILLIS = 1_500L
    }
}
