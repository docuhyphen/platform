package com.docuhyphen.app.api.service.workflow.actions

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.workflow.ActionResult
import com.docuhyphen.app.api.service.workflow.WorkflowActionHandler
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Built-in action handler that advances a workflow subject exchange to
 * [ExchangeStatus.ACCEPTED_STARTED] and emits the `exchange.activated` domain event.
 *
 * Register via `actionHandlerKey = "exchange.auto-accept"` in a workflow step spec.
 */
@ApplicationScoped
class ExchangeAutoAcceptActionHandler : WorkflowActionHandler
{
    private val logger = LoggerFactory.getLogger(ExchangeAutoAcceptActionHandler::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    @Inject private lateinit var exchangeRepository: ExchangeRepository
    @Inject private lateinit var eventPublisher: DomainEventPublisher

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

        exchange.status = ExchangeStatus.ACCEPTED_STARTED
        exchangeRepository.update(exchange)
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
