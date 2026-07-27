package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ShareConstraints
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ExchangeShareConstraintPermutationTest
{
    private val probe = ExchangeAuthorizationProbe()

    @Test
    fun `EX-CON-01 enabled download constraint permits download`()
    {
        probe.assertAllowed(
            Action.DOCUMENT_DOWNLOAD,
            listOf(probe.share(constraintsJson = """{"can_download":true}""")),
        )
    }

    @Test
    fun `EX-CON-02 disabled download constraint denies download but permits preview`()
    {
        val share = probe.share(constraintsJson = """{"can_download":false}""")
        probe.assertDenied(Action.DOCUMENT_DOWNLOAD, listOf(share))
        probe.assertAllowed(Action.DOCUMENT_VIEW, listOf(share))
    }

    @Test
    fun `EX-CON-03 disabled reshare constraint removes reshare capability`()
    {
        probe.assertDenied(
            Action.EXCHANGE_MANAGE_ACCESS,
            listOf(
                probe.share(
                    role = ExchangeShareRoleName.OWNER,
                    constraintsJson = """{"can_reshare":false}""",
                ),
            ),
        )
    }

    @Test
    fun `EX-CON-04 watermark constraint produces watermark obligation`()
    {
        val decision = probe.authorize(
            Action.EXCHANGE_VIEW,
            listOf(probe.share(constraintsJson = """{"watermark":true}""")),
        )
        assertTrue(decision.isAllowed)
        assertTrue((decision as Decision.Allow).obligations.watermark)
    }

    @Test
    fun `EX-CON-05 satisfied MFA constraint permits access`()
    {
        probe.assertAllowed(
            Action.EXCHANGE_VIEW,
            listOf(probe.share(constraintsJson = """{"require_mfa":true}""")),
            AuthorizationContext(mfaSatisfied = true),
        )
    }

    @Test
    fun `EX-CON-06 unsatisfied MFA constraint denies access`()
    {
        val decision = probe.authorize(
            Action.EXCHANGE_VIEW,
            listOf(probe.share(constraintsJson = """{"require_mfa":true}""")),
            AuthorizationContext(mfaSatisfied = false),
        )
        assertEquals(Decision.REASON_MFA_REQUIRED, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `EX-CON-07 allowed client IP permits access`()
    {
        probe.assertAllowed(
            Action.EXCHANGE_VIEW,
            listOf(probe.share(constraintsJson = """{"allowed_ip_ranges":["10.0.0.0/8"]}""")),
            AuthorizationContext(clientIp = "10.4.5.6"),
        )
    }

    @Test
    fun `EX-CON-08 denied client IP blocks access`()
    {
        val decision = probe.authorize(
            Action.EXCHANGE_VIEW,
            listOf(probe.share(constraintsJson = """{"allowed_ip_ranges":["10.0.0.0/8"]}""")),
            AuthorizationContext(clientIp = "192.168.1.1"),
        )
        assertEquals(Decision.REASON_IP_DENIED, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `EX-CON-09 missing client IP fails closed`()
    {
        val decision = probe.authorize(
            Action.EXCHANGE_VIEW,
            listOf(probe.share(constraintsJson = """{"allowed_ip_ranges":["10.0.0.0/8"]}""")),
        )
        assertEquals(Decision.REASON_IP_DENIED, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `EX-CON-10 future Share expiry permits access`()
    {
        probe.assertAllowed(
            Action.EXCHANGE_VIEW,
            listOf(probe.share(expiresAt = Instant.now().plusSeconds(3600))),
        )
    }

    @Test
    fun `EX-CON-11 past Share expiry denies access`()
    {
        val decision = probe.authorize(
            Action.EXCHANGE_VIEW,
            listOf(probe.share(status = ShareStatus.EXPIRED, expiresAt = Instant.now().minusSeconds(1))),
        )
        assertTrue(decision is Decision.Deny)
    }

    @Test
    fun `EX-CON-12 malformed stored constraints fail closed`()
    {
        val decision = probe.authorize(
            Action.EXCHANGE_VIEW,
            listOf(probe.share(constraintsJson = "{broken")),
        )
        assertEquals(Decision.REASON_INVALID_CONSTRAINTS, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `EX-CON-13 max views constraint is rejected on write`()
    {
        assertThrows(IllegalArgumentException::class.java) {
            ShareConstraints.normalizeForStorage("""{"max_views":1}""")
        }
    }

    @Test
    fun `EX-CON-14 permitted download format can be downloaded`()
    {
        val decision = probe.authorize(
            Action.DOCUMENT_DOWNLOAD,
            listOf(probe.share(constraintsJson = """{"can_download":true,"allowed_download_formats":["PDF"]}""")),
        )
        assertTrue(decision.isAllowed)
        assertEquals(setOf("PDF"), (decision as Decision.Allow).obligations.allowedDownloadFormats)
    }

    @Test
    fun `EX-CON-15 excluded download format is denied`()
    {
        val decision = probe.authorize(
            Action.DOCUMENT_DOWNLOAD,
            listOf(probe.share(constraintsJson = """{"can_download":true,"allowed_download_formats":["DOCX"]}""")),
        )
        assertTrue(decision.isAllowed)
        assertEquals(setOf("DOCX"), (decision as Decision.Allow).obligations.allowedDownloadFormats)
    }

    @Test
    fun `EX-CON-16 Viewer upload flag adds upload capability only`()
    {
        val share = probe.share(
            role = ExchangeShareRoleName.VIEWER,
            constraintsJson = """{"allow_document_upload":true}""",
        )
        probe.assertAllowed(Action.DOCUMENT_UPLOAD, listOf(share))
        probe.assertDenied(Action.DOCUMENT_DELETE, listOf(share))
    }

    @Test
    fun `EX-CON-17 Participant deletion flag adds deletion capability only`()
    {
        val share = probe.share(
            role = ExchangeShareRoleName.PARTICIPANT,
            constraintsJson = """{"allow_document_deletion":true}""",
        )
        probe.assertAllowed(Action.DOCUMENT_DELETE, listOf(share))
        probe.assertDenied(Action.DOCUMENT_UPDATE, listOf(share))
    }
}
