package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.exception.StepUpRequiredException
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class InformationRequestRequirementDisclosureTest {
    private val gate = mock<InformationRequestMutationGate>()
    private val requirements = mock<InformationRequestRequirementRepository>()
    private val disclosure = InformationRequestRequirementDisclosure(gate, requirements)
    private val requestId = UUID.randomUUID()
    private val requirementId = UUID.randomUUID()
    private val unverified =
        RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(mfaSatisfied = false))

    @Test
    fun `a signed-in caller who could not see the content even after verifying is refused without a step-up`() {
        whenever(requirements.findCompartmentedIds(any())).thenReturn(setOf(requirementId))
        whenever(gate.permitsRequirement(any(), any(), eq(requirementId))).thenReturn(false)

        assertThrows<ForbiddenException> { disclosure.requireContent(unverified, requestId, requirementId) }
    }

    @Test
    fun `a signed-in caller whom verifying would admit is asked to verify again`() {
        whenever(requirements.findCompartmentedIds(any())).thenReturn(setOf(requirementId))
        whenever(gate.permitsRequirement(argThat { authorization.mfaSatisfied }, any(), eq(requirementId))).thenReturn(
            true
        )

        assertThrows<StepUpRequiredException> { disclosure.requireContent(unverified, requestId, requirementId) }
    }
}
