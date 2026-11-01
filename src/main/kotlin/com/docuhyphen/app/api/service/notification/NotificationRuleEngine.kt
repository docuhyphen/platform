@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.notification.DeliveryTask
import com.docuhyphen.app.api.model.notification.DomainEvent
import com.docuhyphen.app.api.model.notification.NotifAssigneeSpec
import com.docuhyphen.app.api.model.notification.NotificationPredicateExpr
import com.docuhyphen.app.api.repository.notification.NotificationPreferenceRepository
import com.docuhyphen.app.api.repository.notification.NotificationRuleRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.workflow.WorkflowAssigneeResolver
import com.docuhyphen.app.api.service.workflow.WorkflowSpecJson
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class NotificationRuleEngine
{
    private val logger = LoggerFactory.getLogger(NotificationRuleEngine::class.java)

    @Inject
    private lateinit var ruleRepository: NotificationRuleRepository
    @Inject
    private lateinit var preferenceRepository: NotificationPreferenceRepository
    @Inject
    private lateinit var assigneeResolver: WorkflowAssigneeResolver

    private val json: Json = WorkflowSpecJson.instance

    fun resolveDeliveries(event: DomainEvent): List<DeliveryTask>
    {
        val orgId = runCatching { event.organizationId?.let(UUID::fromString) }.getOrNull()
        val rules = ruleRepository.findActiveForEvent(event.type, orgId)
        if (rules.isEmpty()) return emptyList()

        val suppressedRecipients = mutableSetOf<UUID>()
        val tasks = mutableListOf<DeliveryTask>()

        for (rule in rules)
        {
            if (!predicateMatches(rule, event)) continue
            val recipients = resolveAssignees(rule, event)
            when (rule.action)
            {
                NotificationRuleAction.SUPPRESS -> suppressedRecipients += recipients.map { it.id }
                NotificationRuleAction.NOTIFY ->
                {
                    for (recipient in recipients)
                    {
                        if (recipient.kind != PrincipalKind.USER) continue
                        if (recipient.id in suppressedRecipients) continue
                        if (event.actor?.kind == PrincipalKind.USER.name && event.actor.id == recipient.id.toString()) continue
                        tasks += buildTasksForUser(event, recipient.id)
                    }
                }
            }
        }
        return tasks
    }

    private fun resolveAssignees(rule: NotificationRule, event: DomainEvent): List<PrincipalRef>
    {
        val specs: List<NotifAssigneeSpec> = runCatching {
            json.decodeFromString(ListSerializer(NotifAssigneeSpec.serializer()), rule.assigneesJson)
        }.getOrElse {
            logger.warn("Bad assignees_json on rule {}: {}", rule.id, it.message)
            return emptyList()
        }

        return specs.flatMap { spec ->
            when (spec)
            {
                is NotifAssigneeSpec.EventPayload -> recipientsFromPayload(event, spec.field)
                else -> assigneeResolver.resolveOne(spec.toWorkflowSpec(), eventToSubjectFields(event))
            }
        }.distinct()
    }

    private fun recipientsFromPayload(event: DomainEvent, field: String): List<PrincipalRef>
    {
        val raw = event.payload[field] ?: return emptyList()
        return raw.split(",").mapNotNull { token ->
            val parts = token.trim().split(":")
            if (parts.size != 2) return@mapNotNull null
            val kind = runCatching { PrincipalKind.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val id = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return@mapNotNull null
            PrincipalRef(kind, id)
        }
    }

    private fun eventToSubjectFields(event: DomainEvent): Map<String, String>
    {
        val fields = HashMap(event.payload)
        event.subject?.let { fields["subjectId"] = it.id; fields["subjectType"] = it.type }
        event.organizationId?.let { fields["orgId"] = it }
        return fields
    }

    private fun predicateMatches(rule: NotificationRule, event: DomainEvent): Boolean
    {
        val pj = rule.predicateJson?.takeIf { it.isNotBlank() } ?: return true
        return runCatching {
            val parsed = json.decodeFromString(NotificationPredicateExpr.serializer(), pj)
            val actual = when (parsed.field)
            {
                "subject.type" -> event.subject?.type
                "subject.id" -> event.subject?.id
                "organizationId" -> event.organizationId
                else -> event.payload[parsed.field]
            }
            actual == parsed.equals_
        }.getOrElse {
            logger.warn("Bad predicate_json on rule {}: {}", rule.id, it.message)
            false
        }
    }

    private fun buildTasksForUser(event: DomainEvent, appUserId: UUID): List<DeliveryTask>
    {
        val prefs = preferenceRepository.findActiveForUser(appUserId)
        val channels = LinkedHashSet<NotificationChannelType>()

        if (prefs.isEmpty())
        {
            channels += NotificationChannelType.IN_APP
            channels += NotificationChannelType.EMAIL
        }
        else
        {
            for (pref in prefs)
            {
                if (!matchesPattern(pref.eventPattern, event.type)) continue
                channels += pref.channels.split(",").mapNotNull {
                    runCatching { NotificationChannelType.valueOf(it.trim()) }.getOrNull()
                }
            }
            if (channels.isEmpty()) channels += NotificationChannelType.IN_APP
        }

        return channels.map { DeliveryTask(event = event, recipientUserId = appUserId, channel = it) }
    }

    private fun matchesPattern(pattern: String, eventType: String): Boolean
    {
        if (pattern == "*" || pattern == eventType) return true
        if (!pattern.contains("*")) return false
        val regex = "^" + Regex.escape(pattern).replace("\\*", ".*") + "$"
        return Regex(regex).matches(eventType)
    }
}
