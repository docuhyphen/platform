package com.docuhyphen.app.api.service.workflow.actions

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.exchange.ExchangeLifecycleActor
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExchangeLifecycleAudit
import com.docuhyphen.app.api.service.exchange.ExchangeStatusTransitions
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentLifecycleService
import com.docuhyphen.app.api.model.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.model.workflow.ActionResult
import com.docuhyphen.app.api.service.workflow.WorkflowActionHandler
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class ExchangeAutoAcceptActionHandler : WorkflowActionHandler
{
    private val logger = LoggerFactory.getLogger(ExchangeAutoAcceptActionHandler::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    @Inject
    private lateinit var exchangeRepository: ExchangeRepository
    @Inject
    private lateinit var eventPublisher: DomainEventPublisher

    @Inject
    private lateinit var lifecycleAudit: ExchangeLifecycleAudit

    @Inject
    private lateinit var requestParentLifecycle: InformationRequestParentLifecycleService

    override fun key() = "exchange.auto-accept"

    override fun execute(instance: WorkflowInstance, step: WorkflowStepInstance): ActionResult
    {
        val exchangeId = instance.subjectResourceId
            ?: return ActionResult(false, "No subjectResourceId on workflow instance ${instance.id}")

        val exchange = exchangeRepository.findById(exchangeId)
            ?: return ActionResult(false, "Exchange $exchangeId not found")

        if (exchange.status == ExchangeStatus.ACCEPTED_STARTED)
        {
            logger.debug("Exchange {} is already ACCEPTED_STARTED; auto-accept is a no-op", exchangeId)
            return ActionResult(true)
        }

        if (exchange.isDeleted || !ExchangeStatusTransitions.permits(
                exchange.status,
                ExchangeStatus.ACCEPTED_STARTED
            )
        ) {
            return ActionResult(false, "Exchange $exchangeId is ${exchange.status} and cannot be accepted")
        }

        val previousStatus = exchange.status
        exchange.status = ExchangeStatus.ACCEPTED_STARTED
        exchangeRepository.update(exchange)
        lifecycleAudit.recordTransition(
            exchangeId,
            exchange.name,
            previousStatus,
            ExchangeStatus.ACCEPTED_STARTED,
            ExchangeLifecycleActor.WORKFLOW,
        )
        requestParentLifecycle.apply(
            exchangeId,
            ExchangeStatus.ACCEPTED_STARTED,
            false,
            InformationRequestAuditActor.system(PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0))),
            previousStatus,
        )
        logger.info("Exchange {} auto-accepted by workflow instance {}", exchangeId, instance.id)

        val payload = mutableMapOf("instanceId" to instance.id.toString())
        subjectData(instance)["initiatorId"]?.let { payload["initiator"] = "USER:$it" }
        eventPublisher.publish(
            DomainEvent(
                type = "exchange.activated",
                organizationId = instance.organizationId?.toString(),
                subject = DomainEvent.SubjectRef(ResourceType.EXCHANGE.name, exchangeId.toString()),
                payload = payload,
            )
        )
        return ActionResult(true)
    }

    private fun subjectData(instance: WorkflowInstance): Map<String, String> =
        runCatching {
            instance.subjectDataJson?.let {
                json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it)
            }
        }.getOrNull() ?: emptyMap()
}
