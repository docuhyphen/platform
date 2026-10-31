package com.docuhyphen.app.api.service.informationrequest.workflow

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.WorkflowStepType
import com.docuhyphen.app.api.model.notification.DomainEventConsumptionOutcome
import com.docuhyphen.app.api.model.workflow.AssigneeSpec
import com.docuhyphen.app.api.model.workflow.StepOutcomeSpec
import com.docuhyphen.app.api.model.workflow.WorkflowSpec
import com.docuhyphen.app.api.model.workflow.WorkflowStepSpec
import com.docuhyphen.app.api.repository.notification.DomainEventConsumptionRepository
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.EventRouter
import com.docuhyphen.app.api.service.workflow.WorkflowSpecJson
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestWorkflowTriggerDeliveryTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var eventRouter: EventRouter

    @Inject
    lateinit var consumption: DomainEventConsumptionRepository

    @Test
    fun `a delivery whose matching organization definition can never start is recorded as skipped instead of failing`() {
        val organizationId = insertOrganization()
        val definitionId = insertDefinition(organizationId, "{\"steps\": [{\"type\": ")
        val requestId = UUID.randomUUID()
        val event = submission(requestId, organizationId)

        eventRouter.routeDurable(event)
        eventRouter.routeDurable(event)

        assertEquals(
            DomainEventConsumptionOutcome.SKIPPED,
            QuarkusTransaction.requiringNew()
                .call {
                    consumption.outcomeOf(
                        InformationRequestWorkflowTriggerConsumer.CONSUMER_KEY,
                        event.eventUuid()
                    )
                },
        )
        dataSource.connection.use { connection ->
            assertEquals(
                "WORKFLOW_START_REFUSED:$definitionId:SPEC_UNREADABLE",
                queryString(
                    connection,
                    "SELECT detail FROM domain_event_consumption WHERE consumer_key = ? AND event_id = ?",
                    InformationRequestWorkflowTriggerConsumer.CONSUMER_KEY,
                    event.eventUuid(),
                ),
            )
            assertEquals(
                0,
                queryInt(connection, "SELECT count(*) FROM workflow_instance WHERE subject_resource_id = ?", requestId)
            )
        }
    }

    @Test
    fun `a definition whose first step names a malformed principal refuses every matching start of the trigger`() {
        val organizationId = insertOrganization()
        insertDefinition(
            organizationId,
            steps(
                WorkflowStepSpec(
                    type = WorkflowStepType.WAIT_FOR_COUNTERPARTY_CLEARANCE,
                    onApprove = StepOutcomeSpec("END")
                )
            ),
        )
        val malformed = insertDefinition(
            organizationId,
            steps(
                WorkflowStepSpec(
                    type = WorkflowStepType.APPROVAL,
                    assignees = listOf(AssigneeSpec.Principal(PrincipalKind.USER, "not-an-identifier")),
                    onApprove = StepOutcomeSpec("END"),
                    onReject = StepOutcomeSpec("END"),
                ),
            ),
        )
        val requestId = UUID.randomUUID()
        val event = submission(requestId, organizationId)

        eventRouter.routeDurable(event)

        dataSource.connection.use { connection ->
            assertEquals(
                "SKIPPED|WORKFLOW_START_REFUSED:$malformed:ASSIGNEE_INVALID",
                queryString(
                    connection,
                    "SELECT outcome || '|' || detail FROM domain_event_consumption WHERE consumer_key = ? AND event_id = ?",
                    InformationRequestWorkflowTriggerConsumer.CONSUMER_KEY,
                    event.eventUuid(),
                ),
            )
            assertEquals(
                0,
                queryInt(connection, "SELECT count(*) FROM workflow_instance WHERE subject_resource_id = ?", requestId)
            )
        }
    }

    private fun submission(requestId: UUID, organizationId: UUID) = DomainEvent(
        type = "information_request.request.submit",
        subject = DomainEvent.SubjectRef("INFORMATION_REQUEST", requestId.toString()),
        organizationId = organizationId.toString(),
        payload = mapOf("toState" to "SUBMITTED", "sequenceNumber" to "3"),
        orderingKey = "information_request:$requestId",
    )

    private fun insertDefinition(organizationId: UUID, stepsJson: String): UUID {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO workflow_definition
                    (id, name, version, scope, organization_id, trigger_event, steps_json, is_active, is_published, created_at)
                VALUES (?, ?, 1, 'ORG', ?, 'information_request.request.submit', ?, TRUE, TRUE, now())
                """.trimIndent(),
                id,
                "Submission check ${id.toString().take(8)}",
                organizationId,
                stepsJson,
            )
        }
        return id
    }

    private fun insertOrganization(): UUID {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO organization (id, name, registration_number, is_active, verification_complete, created_date)
                VALUES (?, 'Process Owner', ?, TRUE, TRUE, now())
                """.trimIndent(),
                id,
                "REG-${id.toString().take(8)}",
            )
            execute(
                connection,
                """
                INSERT INTO organization_subscription_policy
                    (id, organization_id, tier_code, subscription_status, max_users, created_date, updated_date)
                VALUES (?, ?, 'BUSINESS', 'ACTIVE', 5, now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                id,
            )
        }
        return id
    }

    private fun steps(vararg steps: WorkflowStepSpec): String =
        WorkflowSpecJson.encode(WorkflowSpec(steps = steps.toList()))
}
