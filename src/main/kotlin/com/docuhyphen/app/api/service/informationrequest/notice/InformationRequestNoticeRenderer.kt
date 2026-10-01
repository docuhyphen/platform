package com.docuhyphen.app.api.service.informationrequest.notice

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeIntent
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeSourceKind
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestNoticeContent
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestNoticeDefaults
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestRenderedNotice
import com.docuhyphen.app.api.model.variable.SequenceAllocation
import com.docuhyphen.app.api.service.communication.CommunicationResolver
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.variable.TemplateVariableInterpolator
import com.docuhyphen.app.api.service.variable.VariableResolutionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.security.MessageDigest
import java.time.Clock

@ApplicationScoped
class InformationRequestNoticeRenderer @Inject constructor(
    private val communications: CommunicationResolver,
    private val interpolator: TemplateVariableInterpolator,
    private val appUserService: AppUserService,
    private val organizations: OrganizationGroupService,
    private val clock: Clock,
)
{
    fun render(
        request: InformationRequest,
        intent: InformationRequestNoticeIntent,
        overrides: Map<String, String>,
    ): InformationRequestRenderedNotice
    {
        val communication = intent.sourceCommunicationId?.let(communications::sourceOf)
        val source = communication?.let { InformationRequestNoticeContent(it.subject, it.body) }
            ?: InformationRequestNoticeDefaults.contentOf(intent.noticeKind)
        val organization = request.ownerOrganizationId?.let(organizations::getOrganizationById)
        val allocations = organization?.let { owner ->
            sequenceKeysOf(source).mapNotNull { key -> interpolator.allocateSequence(owner.id, key) }
        }.orEmpty()
        val context = VariableResolutionContext(
            user = senderOf(request),
            organization = organization,
            timestamp = clock.instant(),
            overrides = overrides,
        )
        val subject = interpolator.interpolate(withSequences(source.subject, allocations), context).resolved
        val body = interpolator.interpolate(withSequences(source.body, allocations), context).resolved
        return InformationRequestRenderedNotice(
            subject = subject,
            body = body,
            sourceKind = if (communication != null) InformationRequestNoticeSourceKind.COMMUNICATION else InformationRequestNoticeSourceKind.PLATFORM_DEFAULT,
            sourceCommunicationId = communication?.id,
            sourceContentHash = sha256(source.subject, source.body),
            renderedContentHash = sha256(subject, body),
            allocations = allocations,
        )
    }

    private fun sequenceKeysOf(content: InformationRequestNoticeContent): List<String> =
        (SEQUENCE_TOKEN.findAll(content.subject) + SEQUENCE_TOKEN.findAll(content.body))
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    private fun withSequences(template: String, allocations: List<SequenceAllocation>): String
    {
        val byKey = allocations.associateBy { it.key }
        return SEQUENCE_TOKEN.replace(template) { match -> byKey[match.groupValues[1].trim()]?.renderedValue ?: match.value }
    }

    private fun senderOf(request: InformationRequest): AppUser =
        (request.createdByAppUserId ?: request.ownerUserId)?.let(appUserService::getById) ?: AppUser()

    private fun sha256(subject: String, body: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$subject\u0000$body".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object
    {
        val SEQUENCE_TOKEN = Regex("""\{\{\s*SEQ:([^}]+)}}""")
    }
}
