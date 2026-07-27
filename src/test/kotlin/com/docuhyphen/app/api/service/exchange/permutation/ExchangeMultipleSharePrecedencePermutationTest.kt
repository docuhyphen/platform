package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.Decision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ExchangeMultipleSharePrecedencePermutationTest
{
    private val probe = ExchangeAuthorizationProbe()

    @Test
    fun `EX-MUL-01 direct Viewer and inherited Editor union capabilities`()
    {
        val shares = listOf(
            probe.share(ExchangeShareRoleName.VIEWER),
            probe.share(ExchangeShareRoleName.EDITOR),
        )
        probe.assertAllowed(Action.DOCUMENT_UPDATE, shares)
        probe.assertAllowed(Action.EXCHANGE_VIEW, shares)
    }

    @Test
    fun `EX-MUL-02 direct Participant and inherited Commenter permit comments`()
    {
        probe.assertAllowed(
            Action.DOCUMENT_COMMENT,
            listOf(
                probe.share(ExchangeShareRoleName.PARTICIPANT),
                probe.share(ExchangeShareRoleName.COMMENTER),
            ),
        )
    }

    @Test
    fun `EX-MUL-03 inherited Reviewer can restore download`()
    {
        probe.assertAllowed(
            Action.DOCUMENT_DOWNLOAD,
            listOf(
                probe.share(ExchangeShareRoleName.VIEWER, constraintsJson = """{"can_download":false}"""),
                probe.share(ExchangeShareRoleName.REVIEWER),
            ),
        )
    }

    @Test
    fun `EX-MUL-04 watermark on either Share produces obligation`()
    {
        val decision = probe.authorize(
            Action.EXCHANGE_VIEW,
            listOf(
                probe.share(),
                probe.share(constraintsJson = """{"watermark":true}"""),
            ),
        )
        assertTrue((decision as Decision.Allow).obligations.watermark)
    }

    @Test
    fun `EX-MUL-05 download format restrictions are intersected`()
    {
        val decision = probe.authorize(
            Action.DOCUMENT_DOWNLOAD,
            listOf(
                probe.share(
                    constraintsJson = """{"can_download":true,"allowed_download_formats":["PDF","DOCX"]}""",
                ),
                probe.share(
                    constraintsJson = """{"can_download":true,"allowed_download_formats":["PDF"]}""",
                ),
            ),
        )
        assertEquals(setOf("PDF"), (decision as Decision.Allow).obligations.allowedDownloadFormats)
    }

    @Test
    fun `EX-MUL-06 expired Share does not override active Share`()
    {
        probe.assertAllowed(
            Action.EXCHANGE_VIEW,
            listOf(
                probe.share(status = ShareStatus.EXPIRED, expiresAt = Instant.now().minusSeconds(1)),
                probe.share(),
            ),
        )
    }

    @Test
    fun `EX-MUL-07 revoked Share does not override active Share`()
    {
        probe.assertAllowed(
            Action.EXCHANGE_VIEW,
            listOf(probe.share(status = ShareStatus.REVOKED), probe.share()),
        )
    }

    @Test
    fun `EX-MUL-08 direct access remains after group Share removal`()
    {
        probe.assertAllowed(
            Action.EXCHANGE_VIEW,
            listOf(probe.share(), probe.share(status = ShareStatus.REVOKED)),
        )
    }

    @Test
    fun `EX-MUL-09 inherited access remains after direct Share revocation`()
    {
        probe.assertAllowed(
            Action.EXCHANGE_VIEW,
            listOf(probe.share(status = ShareStatus.REVOKED), probe.share()),
        )
    }

    @Test
    fun `EX-MUL-10 two shared groups union capabilities without duplicate identity`()
    {
        val shares = listOf(
            probe.share(ExchangeShareRoleName.REVIEWER),
            probe.share(ExchangeShareRoleName.SIGNER),
        )
        probe.assertAllowed(Action.DOCUMENT_COMMENT, shares)
        probe.assertAllowed(Action.DOCUMENT_SIGN, shares)
        assertEquals(2, shares.map { it.id }.distinct().size)
    }
}
