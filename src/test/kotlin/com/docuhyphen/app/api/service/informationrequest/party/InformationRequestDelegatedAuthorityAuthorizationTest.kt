package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.auth.authz.Decision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InformationRequestDelegatedAuthorityAuthorizationTest
{
    @Test
    fun `grant and revoke flow through central Requirement authorization policy facts`()
    {
        val fixture = InformationRequestDelegatedAuthorityAuthorizationFixture()

        val beforeGrant = fixture.authorizeDelegateToRespond()

        assertEquals(Decision.REASON_NO_GRANT, (beforeGrant as Decision.Deny).reasonCode)

        val grant = fixture.grant(fixture.delegate, "grant-delegate-for-response")

        val afterGrant = fixture.authorizeDelegateToRespond()

        assertTrue(afterGrant.isAllowed)

        fixture.revoke(grant, "revoke-delegate-for-response")

        val afterRevoke = fixture.authorizeDelegateToRespond()

        assertEquals(Decision.REASON_NO_GRANT, (afterRevoke as Decision.Deny).reasonCode)
    }
}
