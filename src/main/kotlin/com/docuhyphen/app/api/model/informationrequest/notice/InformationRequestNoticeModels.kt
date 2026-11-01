package com.docuhyphen.app.api.model.informationrequest.notice

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.variable.SequenceAllocation
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import java.util.*

data class InformationRequestNoticeContent(
    val subject: String,
    val body: String,
)

object InformationRequestNoticeDefaults
{
    const val DUE_AT = "DUE_AT"

    private val CONTENT = mapOf(
        InformationRequestNoticeKind.REQUIREMENTS_AMENDED to InformationRequestNoticeContent(
            subject = "The information requested of you has changed",
            body = "The Information Request in {{EXCHANGE_NAME}} changed what it asks for. Open it to review what is now requested.",
        ),
        InformationRequestNoticeKind.RESPONSE_REMINDER to InformationRequestNoticeContent(
            subject = "Reminder: requested information is due {{DUE_AT}}",
            body = "The Information Request in {{EXCHANGE_NAME}} is due by {{DUE_AT}}. Open it to complete what is still outstanding.",
        ),
        InformationRequestNoticeKind.RESPONSE_OVERDUE to InformationRequestNoticeContent(
            subject = "Requested information is overdue",
            body = "The Information Request in {{EXCHANGE_NAME}} was due by {{DUE_AT}} and is not yet complete. Open it to complete what is still outstanding.",
        ),
    )

    private val UNDATED_CONTENT = mapOf(
        InformationRequestNoticeKind.RESPONSE_REMINDER to InformationRequestNoticeContent(
            subject = "Reminder: requested information is still outstanding",
            body = "The Information Request in {{EXCHANGE_NAME}} is still waiting for your response. Open it to complete what is still outstanding.",
        ),
    )

    fun contentOf(kind: InformationRequestNoticeKind, dueKnown: Boolean = true): InformationRequestNoticeContent =
        UNDATED_CONTENT[kind]?.takeUnless { dueKnown } ?: CONTENT.getValue(kind)
}

data class InformationRequestRenderedNotice(
    val subject: String,
    val body: String,
    val sourceKind: InformationRequestNoticeSourceKind,
    val sourceCommunicationId: UUID?,
    val sourceContentHash: String,
    val renderedContentHash: String,
    val allocations: List<SequenceAllocation>,
)

data class InformationRequestNoticeView(
    val intent: InformationRequestNoticeIntent,
    val notice: InformationRequestOutboundNotice?,
    val attempts: List<InformationRequestNoticeDeliveryAttempt>,
    val deliveryState: InformationRequestNoticeDeliveryState,
    val recipientWithheld: Boolean = false,
)

data class InformationRequestNoticeRecipient(
    val principal: PrincipalRef,
    val email: String?,
)

data class InformationRequestNoticeDispatchResult(
    val rendered: Int,
    val delivered: Int,
    val failed: Int,
)
