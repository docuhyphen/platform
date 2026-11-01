package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class InformationRequestRespondentKeyTest {
    private val participant = PrincipalRef.participant(UUID.randomUUID())

    @Test
    fun `one principal always has the same key and every other principal has its own`() {
        val key = InformationRequestRespondentKey.of(participant)

        assertTrue(key.isNotBlank())
        assertEquals(key, InformationRequestRespondentKey.of(PrincipalRef.participant(participant.id)))
        assertNotEquals(key, InformationRequestRespondentKey.of(PrincipalRef.participant(UUID.randomUUID())))
        assertNotEquals(key, InformationRequestRespondentKey.of(PrincipalRef.user(participant.id)))
    }

    @Test
    fun `the key does not show the principal it stands for`() {
        val key = InformationRequestRespondentKey.of(participant)

        assertFalse(key.contains(participant.id.toString()))
        assertFalse(key.contains(participant.kind.name))
    }

    @Test
    fun `answers kept for the current respondent or for no named respondent may be saved`() {
        InformationRequestRespondentKey.requireHeldBy(InformationRequestRespondentKey.of(participant), participant)
        InformationRequestRespondentKey.requireHeldBy(null, participant)
    }

    @Test
    fun `answers kept for another respondent are refused`() {
        val other = InformationRequestRespondentKey.of(PrincipalRef.participant(UUID.randomUUID()))

        val refusal = assertThrows<InformationRequestLifecycleException> {
            InformationRequestRespondentKey.requireHeldBy(other, participant)
        }

        assertEquals(InformationRequestErrorCatalog.RESPONDENT_CHANGED, refusal.reasonCode)
    }
}
