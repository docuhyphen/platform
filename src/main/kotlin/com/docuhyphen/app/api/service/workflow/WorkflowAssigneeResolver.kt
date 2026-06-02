package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RoleScopeType
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.RoleAssignmentRepository
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
 * [com.docuhyphen.app.api.model.entity.WorkflowInstance] at startup — never from the live
 * subject — so the resolution is replay-safe and immune to mid-flow mutations.
 */
@ApplicationScoped
class WorkflowAssigneeResolver
{
    @Inject private lateinit var groupMemberRepository: PrincipalGroupMemberRepository
    @Inject private lateinit var roleAssignmentRepository: RoleAssignmentRepository
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

            is AssigneeSpec.RoleAssignees ->
            {
                val scopeId = spec.scopeIdRef?.let { resolveRef(it, subjectFields) }
                resolvePrincipalsHoldingRole(spec.roleName, spec.scopeType, scopeId)
            }
        }

    // -- helpers --------------------------------------------------------------

    /**
     * Looks up users who currently hold [roleName] in the given scope. For
     * ORG-scope roles, also folds in [OrganizationMembership.roleName] matches so
     * org-role assignment that lives on the membership row is honoured (the
     * canonical org-role storage in iteration 1).
     */
    private fun resolvePrincipalsHoldingRole(
        roleName: String,
        scopeType: RoleScopeType,
        scopeId: UUID?,
    ): List<PrincipalRef>
    {
        val viaRoleAssignment = roleAssignmentRepository
            .findAll()
            .asSequence()
            .filter { it.isActive }
            .filter { it.roleName == roleName }
            .filter { it.scopeType == scopeType }
            .filter { scopeType == RoleScopeType.APP || it.scopeId == scopeId }
            .mapNotNull { it.appUserId?.let { uid -> PrincipalRef(PrincipalKind.USER, uid) } }
            .toList()

        val viaMembership = if (scopeType == RoleScopeType.ORG && scopeId != null)
        {
            membershipRepository.findActiveMembersOfOrg(scopeId)
                .asSequence()
                .filter { it.roleName == roleName }
                .map { PrincipalRef(PrincipalKind.USER, it.appUserId) }
                .toList()
        }
        else emptyList()

        return (viaRoleAssignment + viaMembership).distinct()
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

