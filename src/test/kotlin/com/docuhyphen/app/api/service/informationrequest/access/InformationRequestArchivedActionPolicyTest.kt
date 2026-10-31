package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.service.auth.authz.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class InformationRequestArchivedActionPolicyTest {
    @Test
    fun `a finished request stays readable and keeps its closed-record actions while nothing else is allowed`() {
        val requests = InformationRequestAuthorizationContextProvider(mock(), mock())

        assertEquals(
            setOf(
                Action.INFORMATION_REQUEST_VIEW,
                Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
                Action.INFORMATION_REQUEST_EVIDENCE_VIEW,
                Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
                Action.INFORMATION_REQUEST_VIEW_REVIEWS,
                Action.INFORMATION_REQUEST_REQUIREMENT_VIEW_REVIEW,
                Action.INFORMATION_REQUEST_EXPORT,
                Action.INFORMATION_REQUEST_REQUIREMENT_EXPORT,
                Action.INFORMATION_REQUEST_PROMOTE_FACT,
                Action.INFORMATION_REQUEST_REVOKE_FACT,
                Action.INFORMATION_REQUEST_RECORD_DECISION,
                Action.INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES,
                Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES,
                Action.INFORMATION_REQUEST_SUPERSEDE,
                Action.INFORMATION_REQUEST_REQUEST_SUPPLEMENT,
            ),
            requests.actionsWhileArchived,
        )
    }

    @Test
    fun `a Requirement of a finished request stays readable and allows no change`() {
        val requirements = InformationRequestRequirementAuthorizationContextProvider(
            mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(),
        )

        assertEquals(
            setOf(
                Action.INFORMATION_REQUEST_VIEW,
                Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
                Action.INFORMATION_REQUEST_EVIDENCE_VIEW,
                Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
                Action.INFORMATION_REQUEST_VIEW_REVIEWS,
                Action.INFORMATION_REQUEST_REQUIREMENT_VIEW_REVIEW,
                Action.INFORMATION_REQUEST_EXPORT,
                Action.INFORMATION_REQUEST_REQUIREMENT_EXPORT,
            ),
            requirements.actionsWhileArchived,
        )
    }
}
