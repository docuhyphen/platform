package com.docuhyphen.app.api.service.identity

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.ExternalParticipant
import com.docuhyphen.app.api.model.entity.Person
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.identity.PrincipalDisplay
import com.docuhyphen.app.api.repository.exchange.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.user.AppUserRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * How a canonical principal is shown to a person reading a record it is attributed to. The name is
 * the most readable label the platform holds for that principal, and the email is present only for
 * a principal that has one.
 */
class PrincipalDisplayServiceTest
{
    private val appUserRepository: AppUserRepository = mock()
    private val principalGroupRepository: PrincipalGroupRepository = mock()
    private val externalParticipantRepository: ExternalParticipantRepository = mock()

    private val service = PrincipalDisplayService(
        appUserRepository,
        principalGroupRepository,
        externalParticipantRepository,
    )

    @Test
    fun `a registered user is shown by name with their email`()
    {
        val user = AppUser().apply {
            email = "author@process.test"
            person = Person().apply {
                firstName = "Record"
                lastName = "Author"
            }
        }
        whenever(appUserRepository.findById(user.id)).thenReturn(user)

        assertEquals(
            PrincipalDisplay(name = "Record Author", email = "author@process.test"),
            service.display(PrincipalRef.user(user.id)),
        )
    }

    @Test
    fun `a registered user without a name is shown by email`()
    {
        val user = AppUser().apply { email = "author@process.test" }
        whenever(appUserRepository.findById(user.id)).thenReturn(user)

        assertEquals(
            PrincipalDisplay(name = "author@process.test", email = "author@process.test"),
            service.display(PrincipalRef.user(user.id)),
        )
    }

    @Test
    fun `a group is shown by its name and has no email`()
    {
        val group = PrincipalGroup().apply { name = "Review team" }
        whenever(principalGroupRepository.findById(group.id)).thenReturn(group)

        assertEquals(
            PrincipalDisplay(name = "Review team", email = null),
            service.display(PrincipalRef.group(group.id)),
        )
    }

    @Test
    fun `an external participant is shown by email`()
    {
        val participant = ExternalParticipant().apply {
            email = "responder@process.test"
            emailLower = "responder@process.test"
        }
        whenever(externalParticipantRepository.findById(participant.id)).thenReturn(participant)

        assertEquals(
            PrincipalDisplay(name = "responder@process.test", email = "responder@process.test"),
            service.display(PrincipalRef.participant(participant.id)),
        )
    }

    @Test
    fun `a principal the platform cannot resolve is shown as unknown`()
    {
        whenever(appUserRepository.findById(org.mockito.kotlin.any())).thenReturn(null)

        assertEquals(PrincipalDisplay.UNKNOWN, service.display(PrincipalRef.user(UUID.randomUUID())))
        assertEquals(PrincipalDisplay.UNKNOWN, service.display(PrincipalRef.application(UUID.randomUUID())))
    }
}
