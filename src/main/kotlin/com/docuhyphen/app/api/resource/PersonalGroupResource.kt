package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.PrincipalGroupDto
import com.docuhyphen.app.api.model.dto.PrincipalGroupMemberDto
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.PrincipalGroupRepository
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * User-facing resource for managing PERSONAL-scope groups (self-service, no org admin).
 * The owner is **always** the authenticated principal — never from the request body.
 */
@Path("me/groups")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class PersonalGroupResource @Inject constructor(
    private val principalGroupService: PrincipalGroupService,
    private val groupRepository: PrincipalGroupRepository,
    private val memberRepository: PrincipalGroupMemberRepository,
    private val appUserService: AppUserService,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PersonalGroupResource::class.java)
    }

    // ---- DTOs (request bodies) -----------------------------------------------

    @Serializable
    data class CreatePersonalGroupRequest(
        val name: String? = null,
        val description: String? = null,
    )

    @Serializable
    data class UpdatePersonalGroupRequest(
        val name: String? = null,
        val description: String? = null,
    )

    @Serializable
    data class AddMembersRequest(
        val members: List<MemberEntry> = emptyList(),
    )

    @Serializable
    data class MemberEntry(
        val principalId: String? = null,
        val principalKind: String = "USER",
        val groupRole: String = "MEMBER",
    )

    // ---- Endpoints -----------------------------------------------------------

    /** Create a new personal group owned by the authenticated user. */
    @POST
    @Transactional
    fun createGroup(request: CreatePersonalGroupRequest): Response
    {
        return try
        {
            val owner = requirePrincipal()
            val name = request.name?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Group name is required")

            val group = principalGroupService.createPersonalGroup(
                ownerAppUserId = owner.id,
                name = name,
                description = request.description,
            )

            Response.status(CREATED).entity(toDto(group)).build()
        }
        catch (e: Exception)
        {
            handleError(e, "Error creating personal group")
        }
    }

    /** List groups the authenticated user owns **and** groups they are a member of. */
    @GET
    fun listGroups(): Response
    {
        return try
        {
            val principal = requirePrincipal()

            // Groups the user owns.
            val owned = groupRepository.findByOwnerUser(principal.id)

            // Groups the user is a member of (not necessarily the owner).
            val memberOf = memberRepository.findGroupsForPrincipal(PrincipalKind.USER, principal.id)
                .map { it.principalGroupId }
                .filter { gid -> owned.none { it.id == gid } }  // avoid duplicates
                .mapNotNull { groupRepository.findById(it) }
                .filter { it.isActive }

            val all = (owned + memberOf).map { toDto(it) }
            Response.ok(all.toTypedArray()).build()
        }
        catch (e: Exception)
        {
            handleError(e, "Error listing personal groups")
        }
    }

    /** Get a single group by id (authorize GROUP_VIEW). */
    @GET
    @Path("/{groupId}")
    fun getGroup(@PathParam("groupId") groupId: String): Response
    {
        return try
        {
            val gid = UUID.fromString(groupId)
            authorize(Action.GROUP_VIEW, gid)
            val group = groupRepository.findById(gid)
                ?: throw NotFoundException("Group not found")
            Response.ok(toDto(group)).build()
        }
        catch (e: Exception)
        {
            handleError(e, "Error getting personal group")
        }
    }

    /** Rename / update description of a personal group (authorize GROUP_MANAGE_MEMBERS). */
    @PUT
    @Path("/{groupId}")
    @Transactional
    fun updateGroup(
        @PathParam("groupId") groupId: String,
        request: UpdatePersonalGroupRequest,
    ): Response
    {
        return try
        {
            val gid = UUID.fromString(groupId)
            authorize(Action.GROUP_MANAGE_MEMBERS, gid)

            val name = request.name?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Group name is required")

            principalGroupService.renamePersonalGroup(gid, name, request.description)
            val group = groupRepository.findById(gid)!!
            Response.ok(toDto(group)).build()
        }
        catch (e: Exception)
        {
            handleError(e, "Error updating personal group")
        }
    }

    /** Add members to a personal group (authorize GROUP_MANAGE_MEMBERS). */
    @POST
    @Path("/{groupId}/members")
    @Transactional
    fun addMembers(
        @PathParam("groupId") groupId: String,
        request: AddMembersRequest,
    ): Response
    {
        return try
        {
            val gid = UUID.fromString(groupId)
            val principal = requirePrincipal()
            authorize(Action.GROUP_MANAGE_MEMBERS, gid)

            val specs = request.members.map { entry ->
                val pid = entry.principalId?.let { UUID.fromString(it) }
                    ?: throw IllegalArgumentException("principalId is required")
                val kind = runCatching { PrincipalKind.valueOf(entry.principalKind.trim().uppercase()) }
                    .getOrElse { throw IllegalArgumentException("Invalid principalKind: ${entry.principalKind}") }
                val role = runCatching { com.docuhyphen.app.api.model.entity.GroupRole.valueOf(entry.groupRole.trim().uppercase()) }
                    .getOrElse { throw IllegalArgumentException("Invalid groupRole: ${entry.groupRole}") }
                PrincipalGroupService.GroupMemberSpec(principalId = pid, principalKind = kind, groupRole = role)
            }

            principalGroupService.addPersonalMembers(gid, specs, principal.id)

            val group = groupRepository.findById(gid)!!
            Response.ok(toDto(group)).build()
        }
        catch (e: Exception)
        {
            handleError(e, "Error adding members to personal group")
        }
    }

    /** Remove a member from a personal group (authorize GROUP_MANAGE_MEMBERS). */
    @DELETE
    @Path("/{groupId}/members/{principalId}")
    @Transactional
    fun removeMember(
        @PathParam("groupId") groupId: String,
        @PathParam("principalId") principalId: String,
        @QueryParam("principalKind") principalKindParam: String?,
    ): Response
    {
        return try
        {
            val gid = UUID.fromString(groupId)
            val pid = UUID.fromString(principalId)
            authorize(Action.GROUP_MANAGE_MEMBERS, gid)

            val kind = principalKindParam
                ?.let { runCatching { PrincipalKind.valueOf(it.trim().uppercase()) }.getOrNull() }
                ?: PrincipalKind.USER

            principalGroupService.removePersonalMember(gid, kind, pid)
            Response.status(NO_CONTENT).build()
        }
        catch (e: Exception)
        {
            handleError(e, "Error removing member from personal group")
        }
    }

    /** Delete (soft-deactivate) a personal group (authorize GROUP_DELETE). */
    @DELETE
    @Path("/{groupId}")
    @Transactional
    fun deleteGroup(@PathParam("groupId") groupId: String): Response
    {
        return try
        {
            val gid = UUID.fromString(groupId)
            authorize(Action.GROUP_DELETE, gid)
            principalGroupService.deletePersonalGroup(gid)
            Response.status(NO_CONTENT).build()
        }
        catch (e: Exception)
        {
            handleError(e, "Error deleting personal group")
        }
    }

    // ---- helpers -------------------------------------------------------------

    /**
     * Resolve the current authenticated principal or throw.
     * Identity always from session, never from the request body.
     */
    private fun requirePrincipal(): PrincipalRef
    {
        return authorizationContextFactory.currentPrincipal()
            ?: throw ForbiddenException("Authentication required")
    }

    /** Run the standard `authorize()` check for a group resource. */
    private fun authorize(action: Action, groupId: UUID)
    {
        val principal = requirePrincipal()
        val decision = authorizationService.authorize(
            principal = principal,
            action = action,
            resource = ResourceRef.group(groupId),
            context = authorizationContextFactory.currentContext(),
        )
        if (decision is Decision.Deny)
        {
            throw ForbiddenException("Not authorized: ${decision.reasonCode}")
        }
    }

    /** Map a [PrincipalGroup] entity to a [PrincipalGroupDto], including active members. */
    private fun toDto(group: PrincipalGroup): PrincipalGroupDto
    {
        val members = memberRepository.findActiveMembers(group.id).map { member ->
            val user = if (member.principalKind == PrincipalKind.USER)
                appUserService.getById(member.principalId)?.let { DetailedEntityToDtoTransformer.toDto(it) }
            else null
            PrincipalGroupMemberDto(user = user, groupRole = member.groupRole.name)
        }
        return PrincipalGroupDto(
            id = group.id,
            createdDate = group.createdDate,
            isActive = group.isActive,
            name = group.name,
            description = group.description,
            scope = group.scope.name,
            externallyPublished = group.externallyPublished,
            ownerAppUserId = group.ownerAppUserId,
            members = members,
        )
    }

    private fun handleError(e: Exception, logMessage: String): Response
    {
        if (e is jakarta.ws.rs.WebApplicationException) throw e
        return when (e)
        {
            is ForbiddenException ->
            {
                logger.warn("{}: {}", logMessage, e.message)
                Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
            }
            is IllegalArgumentException ->
            {
                logger.warn("{}: {}", logMessage, e.message)
                Response.status(BAD_REQUEST).entity(ResponseError(e.message)).build()
            }
            is NotFoundException ->
            {
                Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
            }
            else ->
            {
                logger.error(logMessage, e)
                Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(logMessage)).build()
            }
        }
    }
}

