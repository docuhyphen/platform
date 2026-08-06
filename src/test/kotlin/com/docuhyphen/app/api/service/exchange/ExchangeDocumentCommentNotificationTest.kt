package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.entity.ExchangeDocumentComment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class ExchangeDocumentCommentNotificationTest
{
    @Test
    fun `identifies the commenter document and Exchange`()
    {
        val message = buildDocumentCommentNotificationMessage(
            commenterName = "Jane Doe",
            documentName = "Signed agreement.pdf",
            exchangeName = "Supplier onboarding",
        )

        assertEquals(
            "Jane Doe commented on \"Signed agreement.pdf\" in Exchange \"Supplier onboarding\".",
            message,
        )
    }

    @Test
    fun `maps page and document version context to the comment response`()
    {
        val versionId = UUID.randomUUID()
        val version = DocumentVersion().apply {
            id = versionId
            version = "3"
        }
        val comment = ExchangeDocumentComment().apply {
            commentText = "Review this clause"
            commentedBy = AppUser().apply { email = "reviewer@example.com" }
            pageNumber = 7
            documentVersion = version
        }

        val dto = DetailedEntityToDtoTransformer.toDto(comment)

        assertEquals(7, dto?.pageNumber)
        assertEquals(versionId.toString(), dto?.documentVersionId)
        assertEquals("3", dto?.documentVersion)
    }
}
