package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Resolves an [AssigneeSpec] (potentially containing `$subject.<field>` placeholders) into
 * the concrete set of [PrincipalRef]s that should receive an APPROVAL step.
 *
 * Placeholder resolution reads from a `subjectDataJson` blob captured on the
 * [com.docuhyphen.app.api.model.entity.WorkflowInstance] at startup, never from the live
 * subject, so the resolution is replay-safe and immune to mid-flow mutations.
 */
@ApplicationScoped
class WorkflowAssigneeResolver
{
    @Inject private lateinit var groupMemberRepository: PrincipalGroupMemberRepository
    @Inject private lateinit var appRoleAssignmentRepository: AppRoleAssignmentRepository
    @Inject private lateinit var membershipRepository: OrganizationMembershipRepository

    private val json: Json = WorkflowSpecJson.instance

    fun resolveAll(specs: List<AssigneeSpec>, subjectDataJson: String?): List<PrincipalRef>
    {
        val subjectFields = parseSubjectFields(subjectDataJson)
        return specs.flatMap { resolveOne(it, subjectFields) }.distinct()
    }

    fun resolveOne(spec: AssigneeSpec, subjectFields: Map<String, String>): List<PrincipalRef> =
        when (spec)
        {
            is AssigneeSpec.Principal -> listOf(
                PrincipalRef(spec.principalKind, UUID.fromString(spec.principalId))
            )

            is AssigneeSpec.GroupRoleAssignees ->
            {
                val groupId = resolveRef(spec.groupIdRef, subjectFields)
                if (groupId == null) emptyList()
                else groupMemberRepository.findActiveMembers(groupId)
                    .filter { it.groupRole == spec.groupRole }
                    .map { PrincipalRef(it.principalKind, it.principalId) }
            }

            is AssigneeSpec.AppRoleAssignees ->
                appRoleAssignmentRepository.findAll()
                    .filter { it.isActive && it.roleName == spec.roleName }
                    .map { PrincipalRef(PrincipalKind.USER, it.appUserId) }

            is AssigneeSpec.OrganizationRoleAssignees ->
                resolveRef(spec.organizationIdRef, subjectFields)?.let { organizationId ->
                    membershipRepository.findActiveMembersOfOrg(organizationId)
                        .filter { spec.roleName in it.roles }
                        .map { PrincipalRef(PrincipalKind.USER, it.appUserId) }
                }.orEmpty()
        }

    /** Resolves a literal UUID, or a `$subject.<field>` placeholder lookup. */
    private fun resolveRef(ref: String, subjectFields: Map<String, String>): UUID?
    {
        val raw = if (ref.startsWith("\$subject."))
            subjectFields[ref.removePrefix("\$subject.")]
        else ref
        return runCatching { raw?.let(UUID::fromString) }.getOrNull()
    }

    private fun parseSubjectFields(subjectDataJson: String?): Map<String, String>
    {
        if (subjectDataJson.isNullOrBlank()) return emptyMap()
        return runCatching {
            (json.parseToJsonElement(subjectDataJson) as? JsonObject)
                ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }
                ?.toMap()
                ?: emptyMap()
        }.getOrDefault(emptyMap())
    }
}

