package com.docuhyphen.app.api.service.informationrequest.occurrence

import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryStrings
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.occurrence.AddInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.InformationRequestGroupOccurrenceResult
import com.docuhyphen.app.api.model.informationrequest.occurrence.RemoveInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.ReorderInformationRequestGroupOccurrencesCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.arc.Arc
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestGroupOccurrenceConcurrencyTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var entityManager: EntityManager

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var requirementRepository: InformationRequestRequirementRepository

    @Inject
    lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var materializer: InformationRequestTemplateMaterializer

    @Inject
    lateinit var occurrences: InformationRequestGroupOccurrenceService

    private val published by lazy {
        PublishedRequestSupport(
            dataSource,
            requestRepository,
            requirementRepository,
            definitionRepository,
            versionRepository,
            capabilityRepository,
            configurationWriter,
            materializer,
        )
    }

    @Test
    fun `remove, reorder, then add keeps every path unique, the order contiguous, and the stored rows on their own paths`() {
        val request = issue("sequential-entries")
        val etags = mutableListOf(published.responseETag(request))
        val first = add(request, "add-first", etags)
        val second = add(request, "add-second", etags)
        val third = add(request, "add-third", etags)

        val removed = command(etags) {
            occurrences.remove(
                RemoveInformationRequestGroupOccurrenceCommand(
                    requestId = request.requestId,
                    access = request.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(etags.last()),
                    idempotencyKey = "remove-second",
                    occurrenceId = second.id,
                ),
            )
        }
        assertEquals(listOf(first.id, third.id), removed.occurrences.map { it.id })
        val reordered = command(etags) {
            occurrences.reorder(
                ReorderInformationRequestGroupOccurrencesCommand(
                    requestId = request.requestId,
                    access = request.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(etags.last()),
                    idempotencyKey = "reorder-remaining",
                    groupKey = ENTRY,
                    orderedOccurrenceIds = listOf(third.id, first.id),
                ),
            )
        }
        assertEquals(listOf(third.id, first.id), reordered.occurrences.map { it.id })
        val fourth = add(request, "add-fourth", etags)

        assertEquals(
            listOf("entry[0]", "entry[1]", "entry[2]", "entry[3]"),
            listOf(first, second, third, fourth).map { it.occurrencePath })
        assertEquals(etags.size, etags.toSet().size, "every command advances the response ETag: $etags")
        assertEquals(published.responseETag(request), etags.last())
        assertEquals(
            listOf("entry[2]" to 0, "entry[0]" to 1, "entry[3]" to 2),
            activeOrder(request),
        )
        assertEquals(
            setOf("entry[0]", "entry[1]", "entry[2]", "entry[3]"),
            requirementPaths(request),
        )
        assertEquals(
            setOf("entry[0]", "entry[1]", "entry[2]", "entry[3]"),
            occurrenceValueSetPaths(request),
        )
        assertEquals(
            4,
            queryRows(
                request,
                "SELECT count(*) FROM information_request_group_occurrence WHERE information_request_id = ?"
            )
        )
    }

    @Test
    fun `two adds racing on any version both commit, each on its own new path`() {
        val request = issue("racing-any-version")
        val (firstOutcome, secondOutcome) = race(
            request,
            CommandPrecondition.Unconditioned,
            CommandPrecondition.Unconditioned
        )

        val paths = listOf(firstOutcome.getOrThrow(), secondOutcome.getOrThrow()).map { newestOf(it).occurrencePath }
        assertEquals(listOf("entry[0]", "entry[1]"), paths)
        assertEquals(
            2,
            queryRows(
                request,
                "SELECT count(*) FROM information_request_group_occurrence WHERE information_request_id = ?"
            )
        )
        assertEquals(
            2,
            queryRows(
                request,
                "SELECT count(DISTINCT occurrence_path) FROM information_request_group_occurrence WHERE information_request_id = ?"
            ),
        )
        assertEquals(setOf("entry[0]", "entry[1]"), requirementPaths(request))
        assertEquals(setOf("entry[0]", "entry[1]"), occurrenceValueSetPaths(request))
        assertEquals(listOf("entry[0]" to 0, "entry[1]" to 1), activeOrder(request))
    }

    @Test
    fun `two adds racing on the same read version let only the first commit and refuse the second as stale`() {
        val request = issue("racing-same-version")
        val read = CommandPrecondition.ExpectedRevision(published.responseETag(request))
        val (firstOutcome, secondOutcome) = race(request, read, read)

        assertEquals("entry[0]", newestOf(firstOutcome.getOrThrow()).occurrencePath)
        val refusal = secondOutcome.exceptionOrNull()
        assertTrue(refusal is CommandPreconditionException, refusal.toString())
        assertEquals(CommandPreconditionException.Kind.STALE, (refusal as CommandPreconditionException).kind)
        assertEquals(published.responseETag(request), refusal.currentETag)
        assertEquals(
            1,
            queryRows(
                request,
                "SELECT count(*) FROM information_request_group_occurrence WHERE information_request_id = ?"
            )
        )
        assertEquals(setOf("entry[0]"), requirementPaths(request))
        assertEquals(setOf("entry[0]"), occurrenceValueSetPaths(request))
    }

    private fun race(
        request: PublishedRequest,
        firstPrecondition: CommandPrecondition,
        secondPrecondition: CommandPrecondition,
    ): Pair<Result<InformationRequestGroupOccurrenceResult>, Result<InformationRequestGroupOccurrenceResult>> {
        val added = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = CompletableFuture.supplyAsync {
            runCatching {
                withRequestContext {
                    QuarkusTransaction.requiringNew().call {
                        val result = occurrences.add(addCommand(request, firstPrecondition, "racing-first"))
                        entityManager.flush()
                        added.countDown()
                        release.await(30, TimeUnit.SECONDS)
                        result
                    }
                }
            }.also { added.countDown() }
        }
        while (!added.await(100, TimeUnit.MILLISECONDS)) {
            if (first.isDone) break
        }
        val second = CompletableFuture.supplyAsync {
            runCatching {
                withRequestContext {
                    QuarkusTransaction.requiringNew().call {
                        occurrences.add(addCommand(request, secondPrecondition, "racing-second"))
                    }
                }
            }
        }
        awaitLockWaiterOrCompletion(second)
        assertTrue(!second.isDone, "the second add waits for the first one's locks")
        release.countDown()
        return first.get(30, TimeUnit.SECONDS) to second.get(30, TimeUnit.SECONDS)
    }

    private fun newestOf(result: InformationRequestGroupOccurrenceResult): InformationRequestGroupOccurrence =
        result.occurrences.maxBy { it.occurrenceIndex }

    private fun addCommand(request: PublishedRequest, precondition: CommandPrecondition, key: String) =
        AddInformationRequestGroupOccurrenceCommand(
            requestId = request.requestId,
            access = request.access(CONTRIBUTOR),
            precondition = precondition,
            idempotencyKey = key,
            groupKey = ENTRY,
        )

    private fun add(
        request: PublishedRequest,
        key: String,
        etags: MutableList<String>
    ): InformationRequestGroupOccurrence {
        val before = QuarkusTransaction.requiringNew().call {
            dataSource.connection.use { connection ->
                queryStrings(
                    connection,
                    "SELECT id::text FROM information_request_group_occurrence WHERE information_request_id = ?",
                    request.requestId
                )
            }
        }
        val result = command(etags) {
            occurrences.add(addCommand(request, CommandPrecondition.ExpectedRevision(etags.last()), key))
        }
        return result.occurrences.single { it.id.toString() !in before }
    }

    private fun command(
        etags: MutableList<String>,
        mutation: () -> InformationRequestGroupOccurrenceResult,
    ): InformationRequestGroupOccurrenceResult {
        val result = QuarkusTransaction.requiringNew().call { mutation() }
        assertNotEquals(etags.last(), result.responseETag)
        etags += result.responseETag
        return result
    }

    private fun activeOrder(request: PublishedRequest): List<Pair<String, Int>> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT occurrence_path, occurrence_index
                FROM information_request_group_occurrence
                WHERE information_request_id = ? AND removed_at IS NULL
                ORDER BY occurrence_index
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, request.requestId)
                statement.executeQuery().use { rows ->
                    val order = mutableListOf<Pair<String, Int>>()
                    while (rows.next()) order += rows.getString(1) to rows.getInt(2)
                    order
                }
            }
        }

    private fun requirementPaths(request: PublishedRequest): Set<String> =
        dataSource.connection.use { connection ->
            queryStrings(
                connection,
                "SELECT occurrence_path FROM information_request_requirement WHERE information_request_id = ? AND occurrence_path <> 'root'",
                request.requestId,
            )
        }

    private fun occurrenceValueSetPaths(request: PublishedRequest): Set<String> =
        dataSource.connection.use { connection ->
            queryStrings(
                connection,
                """
                SELECT value_set.occurrence_path
                FROM field_value_set value_set
                JOIN schema_assignment assignment ON assignment.id = value_set.schema_assignment_id
                WHERE assignment.resource_type = 'INFORMATION_REQUEST' AND assignment.resource_id = ?
                  AND value_set.occurrence_path IS NOT NULL
                """.trimIndent(),
                request.requestId,
            )
        }

    private fun queryRows(request: PublishedRequest, sql: String): Int =
        dataSource.connection.use { connection -> queryInt(connection, sql, request.requestId) }

    private fun awaitLockWaiterOrCompletion(pending: CompletableFuture<*>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!pending.isDone && System.nanoTime() < deadline) {
            val waiting = dataSource.connection.use { connection ->
                queryInt(connection, "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'")
            }
            if (waiting > 0) return
            Thread.onSpinWait()
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

    private fun issue(templateKey: String): PublishedRequest =
        published.issue(templateKey, listOf(LABEL_KEY), listOf(CONTRIBUTOR)) { schemaVersionId, fields ->
            InformationRequestTemplateConfigurationRequest(
                schemaVersionId = schemaVersionId,
                groups = listOf(InformationRequestTemplateGroupRequest(groupKey = ENTRY, maxOccurrences = 6)),
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "entries",
                        title = "Entries",
                        requirements = listOf(
                            InformationRequestTemplateRequirementRequest(
                                requirementKey = LABEL_KEY,
                                requirementType = InformationRequestRequirementType.FIELD,
                                prompt = "Name the entry",
                                responseMode = InformationRequestResponseMode.PROVIDE,
                                requiredness = InformationRequestRequiredness.REQUIRED,
                                contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                                occurrenceAnchorKey = ENTRY,
                                collectedFieldDefinitionId = fields.getValue(LABEL_KEY).fieldDefinitionId,
                                permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                            ),
                        ),
                    ),
                ),
            )
        }

    private companion object {
        const val ENTRY = "entry"
        const val LABEL_KEY = "entry-label"
        const val CONTRIBUTOR = "CONTRIBUTOR"
    }
}
