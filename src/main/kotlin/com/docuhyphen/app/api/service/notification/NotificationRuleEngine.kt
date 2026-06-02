@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.model.entity.NotificationPreference
import com.docuhyphen.app.api.model.entity.NotificationRule
import com.docuhyphen.app.api.model.entity.NotificationRuleAction
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.NotificationPreferenceRepository
import com.docuhyphen.app.api.repository.NotificationRuleRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.workflow.AssigneeSpec
import com.docuhyphen.app.api.service.workflow.WorkflowAssigneeResolver
import com.docuhyphen.app.api.service.workflow.WorkflowSpecJson
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Matches a [DomainEvent] against active [NotificationRule]s, expands assignees to
 * concrete users, applies their [NotificationPreference]s, and emits a list of
 * [DeliveryTask]s for the [DeliveryDispatcher] to act on.
 *
 * Rule resolution order:
 *   1. Org-scoped rules for the event's organization (most specific).
 *   2. App-scoped rules (fallback).
 *   3. Within each tier, ordered by `priority` ASC (lower wins for SUPPRESS chains).
 *
 * Preference resolution (per user, per channel):
 *   * Glob match `event_pattern` against the event type (`session.*` matches `session.activated`).
 *   * Channels intersected against the user's enabled channels for the matched pattern.
 *   * Quiet-hours / digest classification produces the final outcome (INSTANT → dispatch;
 *     DIGEST → also dispatched in iteration 3 because the digest scheduler isn't wired yet,
 *     but the dispatcher will tag the log entry so we can replay them later).
 */
@ApplicationScoped
class NotificationRuleEngine
{
    private val logger = LoggerFactory.getLogger(NotificationRuleEngine::class.java)

    @Inject private lateinit var ruleRepository: NotificationRuleRepository
    @Inject private lateinit var preferenceRepository: NotificationPreferenceRepository
    @Inject private lateinit var assigneeResolver: WorkflowAssigneeResolver

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
                        // De-dup actor → no self-notifications.
                        if (event.actor?.kind == PrincipalKind.USER.name && event.actor.id == recipient.id.toString()) continue
                        tasks += buildTasksForUser(event, recipient.id)
                    }
                }
            }
        }
        return tasks
    }

    // -- assignees ------------------------------------------------------------

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
        // Payload format conventions (kept very simple for iteration 3):
        //   "USER:<uuid>"                                  -> single principal
        //   "USER:<uuid>,USER:<uuid>,PRINCIPAL_GROUP:<id>" -> csv of refs
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

    // -- predicate ------------------------------------------------------------

    /** V3 predicate: trivial `{ "field": "...", "equals": "..." }` form. Null = always match. */
    private fun predicateMatches(rule: NotificationRule, event: DomainEvent): Boolean
    {
        val pj = rule.predicateJson?.takeIf { it.isNotBlank() } ?: return true
        return runCatching {
            val parsed = json.decodeFromString(PredicateExpr.serializer(), pj)
            val actual = when (parsed.field)
            {
                "subject.type"  -> event.subject?.type
                "subject.id"    -> event.subject?.id
                "organizationId" -> event.organizationId
                else            -> event.payload[parsed.field]
            }
            actual == parsed.equals_
        }.getOrElse {
            logger.warn("Bad predicate_json on rule {}: {}", rule.id, it.message)
            false
        }
    }

    @Serializable
    private data class PredicateExpr(
        val field: String,
        // "equals" is a Kotlin Any.equals shadow; rename on the wire.
        @kotlinx.serialization.SerialName("equals") val equals_: String,
    )

    // -- per-user channel resolution -----------------------------------------

    private fun buildTasksForUser(event: DomainEvent, appUserId: UUID): List<DeliveryTask>
    {
        val prefs = preferenceRepository.findActiveForUser(appUserId)
        val channels = LinkedHashSet<NotificationChannelType>()

        if (prefs.isEmpty())
        {
            // Sensible default until the user configures preferences.
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
        // Tiny glob: "session.*" -> matches "session.<anything>"
        val regex = "^" + Regex.escape(pattern).replace("\\*", ".*") + "$"
        return Regex(regex).matches(eventType)
    }

    // -- notification-rule assignee DSL --------------------------------------

    /**
     * Superset of [AssigneeSpec] that adds an `EVENT_PAYLOAD` kind. The other kinds
     * delegate to the workflow assignee resolver — same DSL, single resolver, no drift.
     */
    @Serializable
    @JsonClassDiscriminator("kind")
    sealed class NotifAssigneeSpec
    {
        @Serializable
        @kotlinx.serialization.SerialName("EVENT_PAYLOAD")
        data class EventPayload(val field: String) : NotifAssigneeSpec()

        @Serializable
        @kotlinx.serialization.SerialName("PRINCIPAL")
        data class Principal(
            val principalKind: PrincipalKind,
            val principalId: String,
        ) : NotifAssigneeSpec()

        @Serializable
        @kotlinx.serialization.SerialName("GROUP_ROLE")
        data class GroupRoleAssignees(
            val groupIdRef: String,
            val groupRole: com.docuhyphen.app.api.model.entity.GroupRole,
        ) : NotifAssigneeSpec()

        @Serializable
        @kotlinx.serialization.SerialName("ROLE")
        data class RoleAssignees(
            val roleName: String,
            val scopeType: com.docuhyphen.app.api.model.entity.RoleScopeType,
            val scopeIdRef: String? = null,
        ) : NotifAssigneeSpec()

        /** Bridges PRINCIPAL/GROUP_ROLE/ROLE variants to the workflow assignee DSL. */
        fun toWorkflowSpec(): AssigneeSpec = when (this)
        {
            is Principal -> AssigneeSpec.Principal(principalKind, principalId)
            is GroupRoleAssignees -> AssigneeSpec.GroupRoleAssignees(groupIdRef, groupRole)
            is RoleAssignees -> AssigneeSpec.RoleAssignees(roleName, scopeType, scopeIdRef)
            is EventPayload -> error("EVENT_PAYLOAD must be handled by NotificationRuleEngine, not delegated")
        }
    }
}





