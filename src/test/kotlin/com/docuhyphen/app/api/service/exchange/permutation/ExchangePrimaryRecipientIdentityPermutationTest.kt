package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import com.docuhyphen.app.api.resource.model.ExternalEmailRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.InternalGroupRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.PersonalGroupRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.RegisteredUserRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.TrustedGroupRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.TrustedPersonRecipientSelectionRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ExchangePrimaryRecipientIdentityPermutationTest
{
    @Test
    fun `EX-PRI-01 registered personal user can be primary recipient`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val user = probe.appUser()
        probe.registerUser(user)
        val resolved = probe.resolve(RegisteredUserRecipientSelectionRequest(user.id.toString()), null)
        assertEquals(ExchangeRecipientSelectionType.REGISTERED_USER, resolved.selectionType)
        assertEquals(user.id, resolved.principalId)
    }

    @Test
    fun `EX-PRI-02 same-organization user can be primary recipient`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val user = probe.appUser()
        probe.registerUser(user)
        assertEquals(
            user.id,
            probe.resolve(RegisteredUserRecipientSelectionRequest(user.id.toString())).principalId,
        )
    }

    @Test
    fun `EX-PRI-03 trusted external-organization user can be primary recipient`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val user = probe.appUser()
        val resolutionId = UUID.randomUUID()
        probe.registerTrustedPerson(resolutionId, user)
        val resolved = probe.resolve(TrustedPersonRecipientSelectionRequest(resolutionId.toString()))
        assertEquals(ExchangeRecipientSelectionType.TRUSTED_PERSON, resolved.selectionType)
        assertEquals(probe.targetOrganizationId, resolved.targetOrganizationId)
    }

    @Test
    fun `EX-PRI-04 untrusted external user is denied when a trusted organization is required`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val user = probe.appUser()
        probe.registerUser(user)
        probe.denyUserPolicy(user.id)
        assertThrows(IllegalArgumentException::class.java) {
            probe.resolve(RegisteredUserRecipientSelectionRequest(user.id.toString()))
        }
    }

    @Test
    fun `EX-PRI-05 untrusted external user is allowed when no trusted organization is required`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val user = probe.appUser()
        probe.registerUser(user)
        assertEquals(
            user.id,
            probe.resolve(RegisteredUserRecipientSelectionRequest(user.id.toString())).appUser?.id,
        )
    }

    @Test
    fun `EX-PRI-06 nonexistent external email creates temporary recipient`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val email = "new.recipient@example.test"
        probe.registerEmail(email, null)
        val resolved = probe.resolve(ExternalEmailRecipientSelectionRequest(email, "New", "Recipient"))
        assertEquals(ExchangeRecipientSelectionType.EXTERNAL_EMAIL, resolved.selectionType)
        assertTrue(resolved.appUser?.isTemporary == true)
        assertEquals(email, resolved.appUser?.email)
    }

    @Test
    fun `EX-PRI-07 external email is denied when customer sharing is disabled`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val email = "denied.recipient@example.test"
        probe.registerEmail(email, null)
        probe.denyUserPolicy(null)
        assertThrows(IllegalArgumentException::class.java) {
            probe.resolve(ExternalEmailRecipientSelectionRequest(email, "Denied", "Recipient"))
        }
    }

    @Test
    fun `EX-PRI-08 existing email resolves without duplicate recipient`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val user = probe.appUser()
        val email = "existing.recipient@example.test"
        user.email = email
        probe.registerEmail(email, user)
        val resolved = probe.resolve(ExternalEmailRecipientSelectionRequest(email, "Existing", "Recipient"))
        assertEquals(user.id, resolved.appUser?.id)
        assertFalse(resolved.appUser?.isTemporary == true)
    }

    @Test
    fun `EX-PRI-09 inactive account cannot be selected`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val user = probe.appUser(active = false)
        probe.registerUser(user)
        probe.denyUserPolicy(user.id)
        assertThrows(IllegalArgumentException::class.java) {
            probe.resolve(RegisteredUserRecipientSelectionRequest(user.id.toString()))
        }
    }

    @Test
    fun `EX-PRI-10 deprovisioned account cannot be selected`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val user = probe.appUser(deprovisioned = true)
        probe.registerUser(user)
        probe.denyUserPolicy(user.id)
        assertThrows(IllegalArgumentException::class.java) {
            probe.resolve(RegisteredUserRecipientSelectionRequest(user.id.toString()))
        }
    }

    @Test
    fun `EX-PRI-11 personal group can be primary recipient`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val group = personalGroup(probe.initiator.id)
        probe.registerGroup(group)
        val resolved = probe.resolve(PersonalGroupRecipientSelectionRequest(group.id.toString()), null)
        assertEquals(ExchangeRecipientSelectionType.PERSONAL_GROUP, resolved.selectionType)
    }

    @Test
    fun `EX-PRI-12 internal group can be primary recipient`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val group = organizationGroup(probe.callerOrganizationId)
        probe.registerGroup(group)
        val resolved = probe.resolve(InternalGroupRecipientSelectionRequest(group.id.toString()))
        assertEquals(ExchangeRecipientSelectionType.INTERNAL_GROUP, resolved.selectionType)
    }

    @Test
    fun `EX-PRI-13 published trusted group can be primary recipient`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val group = organizationGroup(probe.targetOrganizationId, published = true)
        probe.registerTrustedGroup(group)
        val resolved = probe.resolve(
            TrustedGroupRecipientSelectionRequest(probe.targetOrganizationId.toString(), group.id.toString()),
        )
        assertEquals(ExchangeRecipientSelectionType.TRUSTED_GROUP, resolved.selectionType)
    }

    @Test
    fun `EX-PRI-14 unpublished external group is denied`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val group = organizationGroup(probe.targetOrganizationId)
        probe.registerTrustedGroup(group, eligible = false)
        assertThrows(IllegalArgumentException::class.java) {
            probe.resolve(
                TrustedGroupRecipientSelectionRequest(probe.targetOrganizationId.toString(), group.id.toString()),
            )
        }
    }

    @Test
    fun `EX-PRI-15 inactive internal group is denied`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val group = organizationGroup(probe.callerOrganizationId).apply { isActive = false }
        probe.registerGroup(group)
        assertThrows(IllegalArgumentException::class.java) {
            probe.resolve(InternalGroupRecipientSelectionRequest(group.id.toString()))
        }
    }

    @Test
    fun `EX-PRI-16 trusted group is denied while trust is suspended`()
    {
        assertTrustedGroupDenied()
    }

    @Test
    fun `EX-PRI-17 trusted group is denied after trust ends`()
    {
        assertTrustedGroupDenied()
    }

    private fun assertTrustedGroupDenied()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val group = organizationGroup(probe.targetOrganizationId, published = true)
        probe.registerTrustedGroup(group, eligible = false)
        assertThrows(IllegalArgumentException::class.java) {
            probe.resolve(
                TrustedGroupRecipientSelectionRequest(probe.targetOrganizationId.toString(), group.id.toString()),
            )
        }
    }

    private fun personalGroup(ownerId: UUID): PrincipalGroup = PrincipalGroup().apply {
        name = "Personal group"
        scope = PrincipalGroupScope.PERSONAL
        ownerAppUserId = ownerId
    }

    private fun organizationGroup(ownerId: UUID, published: Boolean = false): PrincipalGroup =
        PrincipalGroup().apply {
            name = "Organization group"
            scope = PrincipalGroupScope.ORG
            ownerOrganizationId = ownerId
            externallyPublished = published
        }
}
